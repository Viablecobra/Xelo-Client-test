package com.origin.launcher;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DashboardFragment extends Fragment {
    private File currentRootDir = null;
    
    // Activity Result Launchers
    private ActivityResultLauncher<Intent> importBackupLauncher;
    private ActivityResultLauncher<Intent> exportBackupLauncher;
    private ActivityResultLauncher<Intent> importConfigLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    
    // Options.txt editor variables
    private File optionsFile;
    private String originalOptionsContent = "";
    private Stack<String> undoStack = new Stack<>();
    private Stack<String> redoStack = new Stack<>();
    private EditText optionsTextEditor;
    private LinearLayout optionsEditorLayout;
    private TextInputLayout searchInputLayout;
    private TextInputEditText searchEditText;
    private MaterialButton editOptionsButton;
    private SafeTextWatcher optionsTextWatcher;
    
    // Search functionality variables
    private String currentSearchTerm = "";
    private List<Integer> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    private boolean isUpdatingSearchHighlight = false;
    
    // Modules variables
    private File configFile;
    private RecyclerView modulesRecyclerView;
    private ModuleAdapter moduleAdapter;
    private List<ModuleItem> moduleItems;
    
    // Module data class
    private static class ModuleItem {
        private String name;
        private String description;
        private String configKey;
        private boolean enabled;
        
        public ModuleItem(String name, String description, String configKey) {
            this.name = name;
            this.description = description;
            this.configKey = configKey;
            this.enabled = false;
        }
        
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getConfigKey() { return configKey; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
    }
    
    // Module toggle listener interface
    private interface ModuleToggleListener {
        void onToggle(ModuleItem module, boolean isEnabled);
    }
    
    // Safe TextWatcher to prevent memory leaks
    private static class SafeTextWatcher implements TextWatcher {
        private final WeakReference<DashboardFragment> fragmentRef;
        
        public SafeTextWatcher(DashboardFragment fragment) {
            this.fragmentRef = new WeakReference<>(fragment);
        }
        
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
        
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {}
        
        @Override
        public void afterTextChanged(Editable s) {
            DashboardFragment fragment = fragmentRef.get();
            if (fragment != null && !fragment.isUpdatingSearchHighlight) {
                fragment.handleTextChanged(s.toString());
            }
        }
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeActivityResultLaunchers();
    }
    
    private void initializeActivityResultLaunchers() {
        importBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    Uri zipUri = result.getData().getData();
                    if (zipUri != null) {
                        importBackup(zipUri);
                    }
                }
            }
        );
        
        exportBackupLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    Uri saveUri = result.getData().getData();
                    if (saveUri != null && currentRootDir != null) {
                        createBackupAtLocation(saveUri, currentRootDir);
                    }
                }
            }
        );
        
        importConfigLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    Uri configUri = result.getData().getData();
                    if (configUri != null) {
                        importConfig(configUri);
                    }
                }
            }
        );
        
        permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(),
            result -> {
                boolean granted = false;
                for (Boolean permission : result.values()) {
                    if (permission) {
                        granted = true;
                        break;
                    }
                }
                
                if (granted) {
                    if (currentRootDir != null) {
                        openSaveLocationChooser();
                    } else {
                        showToast("No Minecraft data found to backup");
                    }
                } else {
                    showToast("Storage permission is required to backup files");
                }
            }
        );
    }
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        RecyclerView folderRecyclerView = view.findViewById(R.id.folderRecyclerView);
        MaterialButton backupButton = view.findViewById(R.id.backupButton);
        MaterialButton importButton = view.findViewById(R.id.importButton);
        
        if (folderRecyclerView != null) {
            folderRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            setupFolderDisplay(folderRecyclerView);
        }

        if (backupButton != null) {
            backupButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    if (currentRootDir != null) {
                        openSaveLocationChooser();
                    } else {
                        showToast("No Minecraft data found to backup");
                    }
                } else {
                    requestStoragePermissions();
                }
            });
        }

        if (importButton != null) {
            importButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    openFileChooser();
                } else {
                    requestStoragePermissions();
                }
            });
        }

        initializeModules(view);
        initializeOptionsEditor(view);

        return view;
    }
    
    private void setupFolderDisplay(RecyclerView folderRecyclerView) {
        // Find Minecraft data directory
        String[] possiblePaths = {
            "/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/",
            "/storage/emulated/0/games/com.mojang/",
            "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/"
        };
        
        // Add dynamic path as last option
        if (getContext() != null) {
            String dynamicPath = getContext().getExternalFilesDir(null) + "/games/com.mojang/";
            String[] allPaths = new String[possiblePaths.length + 1];
            System.arraycopy(possiblePaths, 0, allPaths, 0, possiblePaths.length);
            allPaths[allPaths.length - 1] = dynamicPath;
            possiblePaths = allPaths;
        }
        
        File rootDir = null;
        for (String path : possiblePaths) {
            File testDir = new File(path);
            if (testDir.exists() && testDir.isDirectory()) {
                File[] testFiles = testDir.listFiles();
                if (testFiles != null && testFiles.length > 0) {
                    rootDir = testDir;
                    currentRootDir = testDir;
                    break;
                }
            }
        }
        
        List<String> folderNames = new ArrayList<>();
        if (rootDir != null && rootDir.exists() && rootDir.isDirectory()) {
            File[] files = rootDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        folderNames.add(file.getName());
                    }
                }
            }
        } else {
            folderNames.add("No Minecraft data found");
        }
        
        FolderAdapter adapter = new FolderAdapter(folderNames);
        folderRecyclerView.setAdapter(adapter);
    }

    private void initializeModules(View view) {
        if (getContext() == null) return;
        
        configFile = new File(getContext().getExternalFilesDir(null), "origin_mods/config.json");
        modulesRecyclerView = view.findViewById(R.id.modulesRecyclerView);
        
        if (modulesRecyclerView != null) {
            modulesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            modulesRecyclerView.setHasFixedSize(true);
            
            // Initialize module items
            moduleItems = new ArrayList<>();
            moduleItems.add(new ModuleItem("No Hurt Cam", "Allows you to toggle the in-game hurt cam", "Nohurtcam"));
            moduleItems.add(new ModuleItem("Fullbright", "Course lets u see in the dark (Doesn't work with No Fog)", "night_vision"));
            moduleItems.add(new ModuleItem("No Fog", "Allows you to toggle the in-game fog (Doesn't work with Fullbright)", "Nofog"));
            moduleItems.add(new ModuleItem("Particles Disabler", "Allows you to toggle the in-game particles", "particles_disabler"));
            moduleItems.add(new ModuleItem("Java Fancy Clouds", "Changes the clouds to Java Fancy Clouds", "java_clouds"));
            moduleItems.add(new ModuleItem("Java Cubemap", "Improves the in-game cubemap bringing it a bit lower", "java_cubemap"));
            moduleItems.add(new ModuleItem("Classic Vanilla Skins", "Disables the newly added skins by Mojang", "classic_skins"));
            moduleItems.add(new ModuleItem("No Flipbook Animation", "Optimizes your FPS by disabling block animation", "no_flipbook_animations"));
            moduleItems.add(new ModuleItem("No Shadows", "Optimizes your FPS by disabling shadows", "no_shadows"));
            moduleItems.add(new ModuleItem("Xelo Title", "Changes the Start screen title image", "xelo_title"));
            moduleItems.add(new ModuleItem("White Block Outline", "Changes the block selection outline to white", "white_block_outline"));
            
            loadModuleStates();
            
            moduleAdapter = new ModuleAdapter(moduleItems, this::onModuleToggle);
            modulesRecyclerView.setAdapter(moduleAdapter);
        }
        
        setupConfigButtons(view);
    }
    
    // Module Adapter class
    private static class ModuleAdapter extends RecyclerView.Adapter<ModuleAdapter.ModuleViewHolder> {
        private List<ModuleItem> modules;
        private ModuleToggleListener toggleListener;
        
        public ModuleAdapter(List<ModuleItem> modules, ModuleToggleListener listener) {
            this.modules = modules;
            this.toggleListener = listener;
        }
        
        @NonNull
        @Override
        public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_module, parent, false);
            return new ModuleViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) {
            ModuleItem module = modules.get(position);
            holder.bind(module, toggleListener);
        }
        
        @Override
        public int getItemCount() {
            return modules.size();
        }
        
        public void updateModules() {
            notifyDataSetChanged();
        }
        
        // ViewHolder class
        static class ModuleViewHolder extends RecyclerView.ViewHolder {
            private TextView moduleNameText;
            private TextView moduleDescriptionText;
            private MaterialSwitch moduleToggleSwitch;
            private ImageView moduleIcon;
            
            public ModuleViewHolder(@NonNull View itemView) {
                super(itemView);
                moduleNameText = itemView.findViewById(R.id.moduleNameText);
                moduleDescriptionText = itemView.findViewById(R.id.moduleDescriptionText);
                moduleToggleSwitch = itemView.findViewById(R.id.moduleToggleSwitch);
                moduleIcon = itemView.findViewById(R.id.moduleIcon);
            }
            
            public void bind(ModuleItem module, ModuleToggleListener listener) {
                moduleNameText.setText(module.getName());
                moduleDescriptionText.setText(module.getDescription());
                moduleToggleSwitch.setChecked(module.isEnabled());
                
                setModuleIcon(module);
                
                moduleToggleSwitch.setOnCheckedChangeListener(null);
                moduleToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    module.setEnabled(isChecked);
                    if (listener != null) {
                        listener.onToggle(module, isChecked);
                    }
                });
                
                itemView.setOnClickListener(v -> {
                    boolean newState = !moduleToggleSwitch.isChecked();
                    moduleToggleSwitch.setChecked(newState);
                });
            }
            
            private void setModuleIcon(ModuleItem module) {
                String configKey = module.getConfigKey();
                int iconRes = R.drawable.wrench; // Default fallback icon
                
                try {
                    switch (configKey) {
                        case "night_vision":
                            // Try system icon, fallback to default if not available
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_view);
                            break;
                        case "Nofog":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_view);
                            break;
                        case "particles_disabler":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_close_clear_cancel);
                            break;
                        case "java_clouds":
                        case "java_cubemap":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_compass);
                            break;
                        case "classic_skins":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_gallery);
                            break;
                        case "no_flipbook_animations":
                        case "no_shadows":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_manage);
                            break;
                        case "xelo_title":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_edit);
                            break;
                        case "white_block_outline":
                            iconRes = getSystemIconOrFallback(android.R.drawable.ic_menu_crop);
                            break;
                        default:
                            iconRes = R.drawable.wrench;
                            break;
                    }
                    
                    moduleIcon.setImageResource(iconRes);
                    
                } catch (Exception e) {
                    // If any icon fails to load, use the wrench icon
                    try {
                        moduleIcon.setImageResource(R.drawable.wrench);
                    } catch (Exception fallbackException) {
                        // If even wrench icon fails, use a simple system icon
                        moduleIcon.setImageResource(android.R.drawable.ic_menu_preferences);
                    }
                }
            }

            // Helper method to safely try system icons
            private int getSystemIconOrFallback(int systemIcon) {
                try {
                    itemView.getContext().getResources().getDrawable(systemIcon);
                    return systemIcon;
                } catch (Exception e) {
                    return R.drawable.wrench;
                }
            }
        } 
    } 

// Helper method to safely try system icons
private int getSystemIconOrFallback(int systemIcon) {
    try {
        // Test if the system icon exists by trying to get it
        itemView.getContext().getResources().getDrawable(systemIcon);
        return systemIcon;
    } catch (Exception e) {
        // If system icon doesn't exist, return wrench icon
        return R.drawable.wrench;
    }
}
    
    private void setupConfigButtons(View view) {
        MaterialButton exportConfigButton = view.findViewById(R.id.exportConfigButton);
        MaterialButton importConfigButton = view.findViewById(R.id.importConfigButton);
        
        if (exportConfigButton != null) {
            exportConfigButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    exportConfig();
                } else {
                    requestStoragePermissions();
                }
            });
        }
        
        if (importConfigButton != null) {
            importConfigButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    openConfigFileChooser();
                } else {
                    requestStoragePermissions();
                }
            });
        }
    }
    
    private void exportConfig() {
        try {
            if (!configFile.exists()) {
                showToast("Config file not found. Creating default config first.");
                createDefaultConfig();
                
                if (!configFile.exists()) {
                    showToast("Failed to create config file.");
                    return;
                }
            }
            
            Uri fileUri = null;
            
            try {
                fileUri = FileProvider.getUriForFile(
                    requireContext(), 
                    "com.origin.launcher.fileprovider", 
                    configFile
                );
            } catch (IllegalArgumentException e) {
                File cacheDir = requireContext().getCacheDir();
                File tempConfigFile = new File(cacheDir, "origin_config.json");
                
                if (tempConfigFile.exists()) {
                    tempConfigFile.delete();
                }
                
                try (FileInputStream fis = new FileInputStream(configFile);
                     FileOutputStream fos = new FileOutputStream(tempConfigFile)) {
                    
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = fis.read(buffer)) > 0) {
                        fos.write(buffer, 0, length);
                    }
                }
                
                if (!tempConfigFile.exists() || tempConfigFile.length() == 0) {
                    showToast("Failed to prepare config file for sharing.");
                    return;
                }
                
                try {
                    fileUri = FileProvider.getUriForFile(
                        requireContext(), 
                        "com.origin.launcher.fileprovider", 
                        tempConfigFile
                    );
                } catch (IllegalArgumentException e2) {
                    showToast("Error: FileProvider configuration issue.");
                    return;
                }
            }
            
            if (fileUri == null) {
                showToast("Failed to create file URI for sharing.");
                return;
            }
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Origin Launcher Config");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "Origin Launcher configuration file");
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            if (shareIntent.resolveActivity(requireContext().getPackageManager()) != null) {
                startActivity(Intent.createChooser(shareIntent, "Export Config File"));
                showToast("Config file ready to share!");
            } else {
                showToast("No apps available to share the config file.");
            }
            
        } catch (IOException e) {
            showToast("Failed to export config: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            showToast("Unexpected error during config export: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void openConfigFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        importConfigLauncher.launch(Intent.createChooser(intent, "Select Config File"));
    }
    
    private void importConfig(Uri configUri) {
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(configUri)) {
            if (inputStream == null) {
                showToast("Could not read the selected config file");
                return;
            }
            
            StringBuilder content = new StringBuilder();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                content.append(new String(buffer, 0, length));
            }
            
            try {
                new JSONObject(content.toString());
            } catch (JSONException e) {
                showToast("Invalid config file format");
                return;
            }
            
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(content.toString());
            }
            
            loadModuleStates();
            showToast("Config imported successfully!");
            
        } catch (IOException e) {
            showToast("Failed to import config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void onModuleToggle(ModuleItem module, boolean isEnabled) {
        updateConfigFile(module.getConfigKey(), isEnabled);
        showToast(module.getName() + " " + (isEnabled ? "enabled" : "disabled"));
    }
    
    private void loadModuleStates() {
        try {
            if (!configFile.exists()) {
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                createDefaultConfig();
                return;
            }
            
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            
            JSONObject config = new JSONObject(content.toString());
            
            for (ModuleItem module : moduleItems) {
                if (config.has(module.getConfigKey())) {
                    module.setEnabled(config.getBoolean(module.getConfigKey()));
                }
            }
            
            if (moduleAdapter != null) {
                moduleAdapter.updateModules();
            }
            
        } catch (IOException | JSONException e) {
            showToast("Failed to load module config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void createDefaultConfig() {
        try {
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    showToast("Failed to create config directory");
                    return;
                }
            }
            
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("Nohurtcam", false);
            defaultConfig.put("night_vision", false);
            defaultConfig.put("Nofog", false);
            defaultConfig.put("particles_disabler", false);
            defaultConfig.put("java_clouds", false);
            defaultConfig.put("java_cubemap", false);
            defaultConfig.put("classic_skins", false);
            defaultConfig.put("no_flipbook_animations", false);
            defaultConfig.put("no_shadows", false);
            defaultConfig.put("white_block_outline", false);
            defaultConfig.put("xelo_title", true);
            
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(defaultConfig.toString(2));
            }
            
        } catch (IOException | JSONException e) {
            showToast("Failed to create default config: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void updateConfigFile(String key, boolean value) {
        try {
            JSONObject config;
            
            if (configFile.exists()) {
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                }
                config = new JSONObject(content.toString());
            } else {
                config = new JSONObject();
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        showToast("Failed to create config directory");
                        return;
                    }
                }
            }
            
            config.put(key, value);
            
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config.toString(2));
            }
            
        } catch (IOException | JSONException e) {
            showToast("Failed to update config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        importBackupLauncher.launch(Intent.createChooser(intent, "Select Backup Zip File"));
    }

    private void openSaveLocationChooser() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "mojang_backup.zip");
        exportBackupLauncher.launch(Intent.createChooser(intent, "Choose where to save backup"));
    }

    private void importBackup(Uri zipUri) {
        try {
            File targetDir = new File("/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/");
            
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    showToast("Could not create target directory: " + targetDir.getAbsolutePath());
                    return;
                }
            }
            
            if (!targetDir.canWrite()) {
                showToast("Cannot write to target directory: " + targetDir.getAbsolutePath());
                return;
            }
            
            showToast("Importing backup to: " + targetDir.getAbsolutePath());
            
            try (InputStream inputStream = requireContext().getContentResolver().openInputStream(zipUri)) {
                if (inputStream != null) {
                    extractZip(inputStream, targetDir);
                    currentRootDir = targetDir;
                    showToast("Backup imported successfully!");
                    refreshFolderList();
                } else {
                    showToast("Could not read the selected file");
                }
            }
        } catch (Exception e) {
            showToast("Import failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void extractZip(InputStream zipInputStream, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[4096];
            
            while ((zipEntry = zis.getNextEntry()) != null) {
                String fileName = zipEntry.getName();
                
                // Enhanced security check for path traversal
                if (fileName.contains("..") || fileName.contains("..%") || 
                    fileName.startsWith("/") || fileName.contains("\\")) {
                    continue;
                }
                
                File newFile = new File(targetDir, fileName);
                
                // Additional security: ensure the file is within target directory
                String canonicalDestPath = targetDir.getCanonicalPath();
                String canonicalDestFile = newFile.getCanonicalPath();
                if (!canonicalDestFile.startsWith(canonicalDestPath + File.separator) && 
                    !canonicalDestFile.equals(canonicalDestPath)) {
                    continue;
                }
                
                if (zipEntry.isDirectory()) {
                    if (!newFile.exists()) {
                        newFile.mkdirs();
                    }
                } else {
                    File parentDir = newFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        parentDir.mkdirs();
                    }
                    
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void refreshFolderList() {
        View view = getView();
        if (view == null) return;
        
        RecyclerView folderRecyclerView = view.findViewById(R.id.folderRecyclerView);
        if (folderRecyclerView != null) {
            setupFolderDisplay(folderRecyclerView);
        }
    }

    private void initializeOptionsEditor(View view) {
        // Use dynamic path resolution for options file
        if (currentRootDir != null) {
            optionsFile = new File(currentRootDir, "minecraftpe/options.txt");
        } else {
            optionsFile = new File("/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/minecraftpe/options.txt");
        }
        
        editOptionsButton = view.findViewById(R.id.editOptionsButton);
        TextView optionsNotFoundText = view.findViewById(R.id.optionsNotFoundText);
        optionsEditorLayout = view.findViewById(R.id.optionsEditorLayout);
        optionsTextEditor = view.findViewById(R.id.optionsTextEditor);
        searchInputLayout = view.findViewById(R.id.searchInputLayout);
        searchEditText = view.findViewById(R.id.searchEditText);
        
        MaterialButton saveOptionsButton = view.findViewById(R.id.saveOptionsButton);
        MaterialButton undoOptionsButton = view.findViewById(R.id.undoOptionsButton);
        MaterialButton redoOptionsButton = view.findViewById(R.id.redoOptionsButton);
        MaterialButton searchOptionsButton = view.findViewById(R.id.searchOptionsButton);
        MaterialButton closeEditorButton = view.findViewById(R.id.closeEditorButton);
        
        if (optionsFile.exists()) {
            editOptionsButton.setVisibility(View.VISIBLE);
            optionsNotFoundText.setVisibility(View.GONE);
            
            editOptionsButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    openOptionsEditor();
                } else {
                    requestStoragePermissions();
                }
            });
        } else {
            editOptionsButton.setVisibility(View.GONE);
            optionsNotFoundText.setVisibility(View.VISIBLE);
        }
        
        if (saveOptionsButton != null) {
            saveOptionsButton.setOnClickListener(v -> saveOptionsFile());
        }
        
        if (undoOptionsButton != null) {
            undoOptionsButton.setOnClickListener(v -> undoChanges());
        }
        
        if (redoOptionsButton != null) {
            redoOptionsButton.setOnClickListener(v -> redoChanges());
        }
        
        if (searchOptionsButton != null) {
            searchOptionsButton.setOnClickListener(v -> {
                if (searchInputLayout.getVisibility() == View.GONE) {
                    toggleSearch();
                } else {
                    String searchTerm = searchEditText.getText().toString().trim();
                    if (!searchTerm.isEmpty()) {
                        findNextMatch(searchTerm);
                    }
                }
            });
        }
        
        if (closeEditorButton != null) {
            closeEditorButton.setOnClickListener(v -> closeOptionsEditor());
        }
        
        if (searchEditText != null) {
            searchEditText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    String searchTerm = s.toString().trim();
                    if (!searchTerm.isEmpty()) {
                        searchInText(searchTerm);
                    } else {
                        clearSearchResults();
                    }
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
            
            searchEditText.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || 
                    (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    String searchTerm = searchEditText.getText().toString().trim();
                    if (!searchTerm.isEmpty()) {
                        findNextMatch(searchTerm);
                    }
                    return true;
                }
                return false;
            });
        }
        
        if (optionsTextEditor != null) {
            optionsTextWatcher = new SafeTextWatcher(this);
            optionsTextEditor.addTextChangedListener(optionsTextWatcher);
            
            optionsTextEditor.setOnTouchListener((v, event) -> {
                v.requestFocus();
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
    }
    
    private void handleTextChanged(String currentText) {
        if (!currentText.equals(originalOptionsContent) && !undoStack.isEmpty() && !currentText.equals(undoStack.peek())) {
            undoStack.push(currentText);
            redoStack.clear();
        }
    }

    private void openOptionsEditor() {
        try {
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(optionsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            originalOptionsContent = content.toString();
            optionsTextEditor.setText(originalOptionsContent);
            
            undoStack.clear();
            redoStack.clear();
            undoStack.push(originalOptionsContent);
            
            optionsEditorLayout.setVisibility(View.VISIBLE);
            editOptionsButton.setEnabled(false);
            editOptionsButton.setText("Editor Open");
            
            showToast("Options.txt loaded successfully");
            
        } catch (IOException e) {
            showToast("Failed to load options.txt: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveOptionsFile() {
        try {
            String content = optionsTextEditor.getText().toString();
            try (FileWriter writer = new FileWriter(optionsFile)) {
                writer.write(content);
            }
            
            originalOptionsContent = content;
            showToast("Options.txt saved successfully");
            
        } catch (IOException e) {
            showToast("Failed to save options.txt: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void undoChanges() {
        if (undoStack.size() > 1) {
            int currentCursorPosition = optionsTextEditor.getSelectionStart();
            String currentText = optionsTextEditor.getText().toString();
            redoStack.push(currentText);
            undoStack.pop();
            String previousText = undoStack.peek();
            optionsTextEditor.setText(previousText);
            
            int safePosition = Math.min(currentCursorPosition, previousText.length());
            optionsTextEditor.setSelection(safePosition);
        } else {
            showToast("Nothing to undo");
        }
    }

    private void redoChanges() {
        if (!redoStack.isEmpty()) {
            int currentCursorPosition = optionsTextEditor.getSelectionStart();
            String redoText = redoStack.pop();
            undoStack.push(redoText);
            optionsTextEditor.setText(redoText);
            
            int safePosition = Math.min(currentCursorPosition, redoText.length());
            optionsTextEditor.setSelection(safePosition);
        } else {
            showToast("Nothing to redo");
        }
    }

    private void toggleSearch() {
        if (searchInputLayout.getVisibility() == View.GONE) {
            searchInputLayout.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
        } else {
            searchInputLayout.setVisibility(View.GONE);
            clearSearchResults();
        }
    }

    private void searchInText(String searchTerm) {
        if (searchTerm.isEmpty()) {
            clearSearchResults();
            return;
        }
        
        currentSearchTerm = searchTerm;
        findAllMatches(searchTerm);
        
        isUpdatingSearchHighlight = true;
        
        String text = optionsTextEditor.getText().toString();
        SpannableString spannable = new SpannableString(text);
        
        // Use color resource instead of hardcoded color
        int highlightColor = ContextCompat.getColor(requireContext(), android.R.color.holo_yellow_light);
        
        for (int matchIndex : searchMatches) {
            spannable.setSpan(
                new BackgroundColorSpan(highlightColor),
                matchIndex,
                matchIndex + searchTerm.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        optionsTextEditor.setText(spannable);
        currentMatchIndex = -1;
        
        isUpdatingSearchHighlight = false;
    }

    private void findAllMatches(String searchTerm) {
        searchMatches.clear();
        String text = optionsTextEditor.getText().toString();
        String lowerText = text.toLowerCase();
        String lowerSearchTerm = searchTerm.toLowerCase();
        
        int index = lowerText.indexOf(lowerSearchTerm);
        while (index >= 0) {
            searchMatches.add(index);
            index = lowerText.indexOf(lowerSearchTerm, index + 1);
        }
    }

    private void clearSearchResults() {
        searchMatches.clear();
        currentMatchIndex = -1;
        currentSearchTerm = "";
        
        isUpdatingSearchHighlight = true;
        String plainText = optionsTextEditor.getText().toString();
        optionsTextEditor.setText(plainText);
        isUpdatingSearchHighlight = false;
    }

    private void closeOptionsEditor() {
        optionsEditorLayout.setVisibility(View.GONE);
        searchInputLayout.setVisibility(View.GONE);
        
        editOptionsButton.setEnabled(true);
        editOptionsButton.setText("Edit options.txt");
        
        undoStack.clear();
        redoStack.clear();
        
        showToast("Editor closed");
    }

    private void findNextMatch(String searchTerm) {
        if (!searchTerm.equals(currentSearchTerm)) {
            currentSearchTerm = searchTerm;
            findAllMatches(searchTerm);
            currentMatchIndex = -1;
        }
        
        if (searchMatches.isEmpty()) {
            showToast("No matches found");
            return;
        }
        
        currentMatchIndex++;
        if (currentMatchIndex >= searchMatches.size()) {
            currentMatchIndex = 0;
        }
        
        int matchPosition = searchMatches.get(currentMatchIndex);
        
        optionsTextEditor.setSelection(matchPosition, matchPosition + searchTerm.length());
        optionsTextEditor.requestFocus();
        
        scrollToPosition(matchPosition);
        
        String message = "Match " + (currentMatchIndex + 1) + " of " + searchMatches.size();
        showToast(message);
    }

    private void scrollToPosition(int position) {
        android.text.Layout layout = optionsTextEditor.getLayout();
        if (layout != null) {
            int line = layout.getLineForOffset(position);
            int lineTop = layout.getLineTop(line);
            int lineBottom = layout.getLineBottom(line);
            int lineHeight = lineBottom - lineTop;
            
            int editorHeight = optionsTextEditor.getHeight();
            int scrollY = Math.max(0, lineTop - (editorHeight / 2) + (lineHeight / 2));
            
            optionsTextEditor.scrollTo(0, scrollY);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String[] permissions = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
            permissionLauncher.launch(permissions);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            showToast("Please grant 'All files access' permission to backup files");
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            permissionLauncher.launch(permissions);
        }
    }

    private void createBackupAtLocation(Uri saveUri, File rootDir) {
        try {
            if (!rootDir.exists()) {
                showToast("Minecraft data directory not found: " + rootDir.getAbsolutePath());
                return;
            }
            
            File[] files = rootDir.listFiles();
            if (files == null || files.length == 0) {
                showToast("No files found to backup in: " + rootDir.getAbsolutePath());
                return;
            }
            
            showToast("Creating backup...");
            
            try (ZipOutputStream zos = new ZipOutputStream(requireContext().getContentResolver().openOutputStream(saveUri))) {
                zipDirectoryToStream(rootDir, rootDir.getAbsolutePath(), zos);
                showToast("Backup saved successfully!");
            }
            
        } catch (Exception e) {
            showToast("Backup failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void zipDirectoryToStream(File sourceDir, String basePath, ZipOutputStream zos) throws IOException {
        zipFileRecursive(sourceDir, basePath, zos);
    }

    private void zipFileRecursive(File fileToZip, String basePath, ZipOutputStream zos) throws IOException {
        if (fileToZip.isHidden()) return;
        if (fileToZip.isDirectory()) {
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFileRecursive(childFile, basePath, zos);
                }
            }
            return;
        }
        
        if (!fileToZip.canRead()) {
            return;
        }
        
        String zipEntryName = fileToZip.getAbsolutePath().replace(basePath, "").replaceFirst("^/", "");
        if (zipEntryName.isEmpty()) {
            zipEntryName = fileToZip.getName();
        }
        
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(zipEntryName);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[1024];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
            zos.closeEntry();
        } catch (IOException e) {
            System.err.println("Skipping file due to error: " + fileToZip.getAbsolutePath() + " - " + e.getMessage());
        }
    }
    
    private void showToast(String message) {
        if (isAdded()) {
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
        }
    }

    private static class FolderAdapter extends RecyclerView.Adapter<FolderViewHolder> {
        private final List<String> folders;
        
        FolderAdapter(List<String> folders) { 
            this.folders = folders; 
        }
        
        @NonNull
        @Override
        public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
            return new FolderViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
            holder.bind(folders.get(position));
        }
        
        @Override
        public int getItemCount() { 
            return folders.size(); 
        }
    }
    
    private static class FolderViewHolder extends RecyclerView.ViewHolder {
        private final TextView textView;
        
        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.folderNameText);
        }
        
        void bind(String folderName) {
            textView.setText(folderName);
        }
    }
    
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Clean up to prevent memory leaks
        if (optionsTextEditor != null && optionsTextWatcher != null) {
            optionsTextEditor.removeTextChangedListener(optionsTextWatcher);
        }
        optionsTextWatcher = null;
    }
}