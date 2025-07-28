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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
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
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DashboardFragment extends Fragment {
    private File currentRootDir = null; // Store the found root directory
    private static final int IMPORT_REQUEST_CODE = 1002;
    private static final int EXPORT_REQUEST_CODE = 1003;
    private static final int IMPORT_CONFIG_REQUEST_CODE = 1004;
    private static final int EXPORT_CONFIG_REQUEST_CODE = 1005;
    
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
    
    // Search functionality variables
    private String currentSearchTerm = "";
    private List<Integer> searchMatches = new ArrayList<>();
    private int currentMatchIndex = -1;
    
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
            this.enabled = false; // Default to disabled
        }
        
        // Getters and setters
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
    
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_dashboard, container, false);

        RecyclerView folderRecyclerView = view.findViewById(R.id.folderRecyclerView);
        MaterialButton backupButton = view.findViewById(R.id.backupButton);
        MaterialButton importButton = view.findViewById(R.id.importButton);
        
        if (folderRecyclerView != null) {
            folderRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

            // File management root - try multiple possible paths
            String[] possiblePaths = {
                "/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/",
                "/storage/emulated/0/games/com.mojang/",
                "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/",
                getContext().getExternalFilesDir(null) + "/games/com.mojang/"
            };
            
            File rootDir = null;
            String rootPath = null;
            
            for (String path : possiblePaths) {
                File testDir = new File(path);
                if (testDir.exists() && testDir.isDirectory()) {
                    File[] testFiles = testDir.listFiles();
                    if (testFiles != null && testFiles.length > 0) {
                        rootDir = testDir;
                        rootPath = path;
                        currentRootDir = testDir; // Store for later use
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

        if (backupButton != null) {
            backupButton.setOnClickListener(v -> {
                if (hasStoragePermission()) {
                    if (currentRootDir != null) {
                        openSaveLocationChooser();
                    } else {
                        Toast.makeText(requireContext(), "No Minecraft data found to backup", Toast.LENGTH_LONG).show();
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

        // Initialize modules
        initializeModules(view);
        
        // Initialize options.txt editor
        initializeOptionsEditor(view);

        return view;
    }

    private void initializeModules(View view) {
        // Initialize config file path
        configFile = new File(getContext().getExternalFilesDir(null), "origin_mods/config.json");
        
        // Get modules RecyclerView
        modulesRecyclerView = view.findViewById(R.id.modulesRecyclerView);
        if (modulesRecyclerView != null) {
            modulesRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
            
            // Initialize module items
            moduleItems = new ArrayList<>();
            moduleItems.add(new ModuleItem("no hurt cam", "allows you to toggle the in-game hurt cam", "Nohurtcam"));
            moduleItems.add(new ModuleItem("No Fog", "allows you to toggle the in-game fog", "Nofog"));
            moduleItems.add(new ModuleItem("Particles Disabler", "allows you to toggle the in-game particles", "particles_disabler"));
            moduleItems.add(new ModuleItem("Java Fancy Clouds", "Changes the clouds to Java Fancy Clouds", "java_clouds"));
            moduleItems.add(new ModuleItem("Java Cubemap", "improves the in-game cubemap bringing it abit lower", "java_cubemap"));
            
            // Create and set adapter
            moduleAdapter = new ModuleAdapter(moduleItems, this::onModuleToggle);
            modulesRecyclerView.setAdapter(moduleAdapter);
            
            // Load current config state
            loadModuleStates();
        }
        
        // Set up the existing XML config buttons
        setupConfigButtons(view);
    }
    
    private void setupConfigButtons(View view) {
        // Find the existing buttons from XML
        MaterialButton exportConfigButton = view.findViewById(R.id.exportConfigButton);
        MaterialButton importConfigButton = view.findViewById(R.id.importConfigButton);
        
        // Set click listeners for the XML buttons
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
                Toast.makeText(requireContext(), "Config file not found. Creating default config first.", Toast.LENGTH_LONG).show();
                createDefaultConfig();
            }
            
            // Create a temporary file in cache for sharing
            File cacheDir = requireContext().getCacheDir();
            File tempConfigFile = new File(cacheDir, "config.json");
            
            // Copy config file to cache
            try (FileInputStream fis = new FileInputStream(configFile);
                 FileOutputStream fos = new FileOutputStream(tempConfigFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = fis.read(buffer)) > 0) {
                    fos.write(buffer, 0, length);
                }
            }
            
            // Share the file
            Uri fileUri = FileProvider.getUriForFile(requireContext(), "com.origin.launcher.fileprovider", tempConfigFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Export Config"));
            Toast.makeText(requireContext(), "Config exported successfully!", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to export config: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void openConfigFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Config File"), IMPORT_CONFIG_REQUEST_CODE);
    }
    
    private void importConfig(Uri configUri) {
        try {
            // Read the selected config file
            InputStream inputStream = requireContext().getContentResolver().openInputStream(configUri);
            if (inputStream == null) {
                Toast.makeText(requireContext(), "Could not read the selected config file", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Read content
            StringBuilder content = new StringBuilder();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                content.append(new String(buffer, 0, length));
            }
            inputStream.close();
            
            // Validate JSON
            try {
                new JSONObject(content.toString());
            } catch (JSONException e) {
                Toast.makeText(requireContext(), "Invalid config file format", Toast.LENGTH_LONG).show();
                return;
            }
            
            // Ensure config directory exists
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Write to config file
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(content.toString());
            }
            
            // Reload module states
            loadModuleStates();
            
            Toast.makeText(requireContext(), "Config imported successfully!", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to import config: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }
    
    private void onModuleToggle(ModuleItem module, boolean isEnabled) {
        updateConfigFile(module.getConfigKey(), isEnabled);
        Toast.makeText(requireContext(), 
            module.getName() + " " + (isEnabled ? "enabled" : "disabled"), 
            Toast.LENGTH_SHORT).show();
    }
    
    private void loadModuleStates() {
        try {
            if (!configFile.exists()) {
                // Create directory if it doesn't exist
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    parentDir.mkdirs();
                }
                // Create default config
                createDefaultConfig();
                return;
            }
            
            // Read existing config
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line);
                }
            }
            
            JSONObject config = new JSONObject(content.toString());
            
            // Update module states
            for (ModuleItem module : moduleItems) {
                if (config.has(module.getConfigKey())) {
                    module.setEnabled(config.getBoolean(module.getConfigKey()));
                }
            }
            
            // Notify adapter of changes
            if (moduleAdapter != null) {
                moduleAdapter.notifyDataSetChanged();
            }
            
        } catch (IOException | JSONException e) {
            Toast.makeText(requireContext(), "Failed to load module config: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void createDefaultConfig() {
        try {
            // Create parent directory if it doesn't exist
            File parentDir = configFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean created = parentDir.mkdirs();
                if (!created) {
                    Toast.makeText(requireContext(), "Failed to create config directory", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
            
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("Nohurtcam", false);
            defaultConfig.put("Nofog", false);
            defaultConfig.put("particles_disabler", false);
            defaultConfig.put("java_clouds", false);
            defaultConfig.put("java_cubemap", false);
            
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(defaultConfig.toString(2)); // Pretty print with indent
            }
            
        } catch (IOException | JSONException e) {
            Toast.makeText(requireContext(), "Failed to create default config: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }
    
    private void updateConfigFile(String key, boolean value) {
        try {
            JSONObject config;
            
            if (configFile.exists()) {
                // Read existing config
                StringBuilder content = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line);
                    }
                }
                config = new JSONObject(content.toString());
            } else {
                // Create new config and ensure directory exists
                config = new JSONObject();
                File parentDir = configFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    boolean created = parentDir.mkdirs();
                    if (!created) {
                        Toast.makeText(requireContext(), "Failed to create config directory", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
            
            // Update the specific key
            config.put(key, value);
            
            // Write back to file
            try (FileWriter writer = new FileWriter(configFile)) {
                writer.write(config.toString(2)); // Pretty print with indent
            }
            
        } catch (IOException | JSONException e) {
            Toast.makeText(requireContext(), "Failed to update config: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private void openFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/zip");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select Backup Zip File"), IMPORT_REQUEST_CODE);
    }

    private void openSaveLocationChooser() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "mojang_backup.zip");
        startActivityForResult(Intent.createChooser(intent, "Choose where to save backup"), EXPORT_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == IMPORT_REQUEST_CODE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri zipUri = data.getData();
            if (zipUri != null) {
                importBackup(zipUri);
            }
        } else if (requestCode == EXPORT_REQUEST_CODE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri saveUri = data.getData();
            if (saveUri != null && currentRootDir != null) {
                createBackupAtLocation(saveUri, currentRootDir);
            }
        } else if (requestCode == IMPORT_CONFIG_REQUEST_CODE && resultCode == getActivity().RESULT_OK && data != null) {
            Uri configUri = data.getData();
            if (configUri != null) {
                importConfig(configUri);
            }
        }
    }

    private void importBackup(Uri zipUri) {
        try {
            // Use the specific target directory
            File targetDir = new File("/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/");
            
            // Create directory if it doesn't exist
            if (!targetDir.exists()) {
                boolean created = targetDir.mkdirs();
                if (!created) {
                    Toast.makeText(requireContext(), "Could not create target directory: " + targetDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                    return;
                }
            }
            
            // Check if we can write to the directory
            if (!targetDir.canWrite()) {
                Toast.makeText(requireContext(), "Cannot write to target directory: " + targetDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            
            Toast.makeText(requireContext(), "Importing backup to: " + targetDir.getAbsolutePath(), Toast.LENGTH_SHORT).show();
            
            InputStream inputStream = requireContext().getContentResolver().openInputStream(zipUri);
            if (inputStream != null) {
                extractZip(inputStream, targetDir);
                
                // Update currentRootDir to the new location
                currentRootDir = targetDir;
                
                Toast.makeText(requireContext(), "Backup imported successfully!", Toast.LENGTH_LONG).show();
                
                // Refresh the folder list
                refreshFolderList();
            } else {
                Toast.makeText(requireContext(), "Could not read the selected file", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Import failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void extractZip(InputStream zipInputStream, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(zipInputStream)) {
            ZipEntry zipEntry;
            byte[] buffer = new byte[4096]; // Increased buffer size
            
            while ((zipEntry = zis.getNextEntry()) != null) {
                String fileName = zipEntry.getName();
                
                // Security check: prevent directory traversal
                if (fileName.contains("..")) {
                    continue;
                }
                
                File newFile = new File(targetDir, fileName);
                
                if (zipEntry.isDirectory()) {
                    // Create directory
                    if (!newFile.exists()) {
                        boolean created = newFile.mkdirs();
                        if (!created) {
                            System.err.println("Failed to create directory: " + newFile.getAbsolutePath());
                        }
                    }
                } else {
                    // Create parent directories if they don't exist
                    File parentDir = newFile.getParentFile();
                    if (parentDir != null && !parentDir.exists()) {
                        boolean created = parentDir.mkdirs();
                        if (!created) {
                            System.err.println("Failed to create parent directory: " + parentDir.getAbsolutePath());
                            continue;
                        }
                    }
                    
                    // Extract file
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                        fos.flush();
                    } catch (IOException e) {
                        System.err.println("Failed to extract file: " + newFile.getAbsolutePath() + " - " + e.getMessage());
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void refreshFolderList() {
        RecyclerView folderRecyclerView = getView().findViewById(R.id.folderRecyclerView);
        if (folderRecyclerView != null) {
            // Re-scan for folders
            String[] possiblePaths = {
                "/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/",
                "/storage/emulated/0/games/com.mojang/",
                "/storage/emulated/0/Android/data/com.mojang.minecraftpe/files/games/com.mojang/",
                getContext().getExternalFilesDir(null) + "/games/com.mojang/"
            };
            
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
    }

    private void initializeOptionsEditor(View view) {
        // Initialize options.txt file path
        optionsFile = new File("/storage/emulated/0/Android/data/com.origin.launcher/files/games/com.mojang/minecraftpe/options.txt");
        
        // Get UI elements
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
        
        // Check if options.txt exists
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
        
        // Set up editor buttons
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
                    // If search is already open, cycle through matches
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
        
        // Set up search functionality
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
                        // Clear search results when search term is empty
                        clearSearchResults();
                    }
                }
                
                @Override
                public void afterTextChanged(Editable s) {}
            });
            
            // Handle Enter key in search
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
        
        // Set up text change listener for undo/redo
        if (optionsTextEditor != null) {
            optionsTextEditor.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                
                @Override
                public void afterTextChanged(Editable s) {
                    // Add to undo stack when text changes
                    String currentText = s.toString();
                    if (!currentText.equals(originalOptionsContent) && !undoStack.isEmpty() && !currentText.equals(undoStack.peek())) {
                        undoStack.push(currentText);
                        redoStack.clear(); // Clear redo stack when new changes are made
                    }
                }
            });
            
            // Handle touch events to ensure proper focus
            optionsTextEditor.setOnTouchListener((v, event) -> {
                v.requestFocus();
                v.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            });
        }
    }

    private void openOptionsEditor() {
        try {
            // Read the options.txt file
            StringBuilder content = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(optionsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }
            
            originalOptionsContent = content.toString();
            optionsTextEditor.setText(originalOptionsContent);
            
            // Initialize undo stack
            undoStack.clear();
            redoStack.clear();
            undoStack.push(originalOptionsContent);
            
            // Show editor and disable edit button
            optionsEditorLayout.setVisibility(View.VISIBLE);
            editOptionsButton.setEnabled(false);
            editOptionsButton.setText("Editor Open");
            
            Toast.makeText(requireContext(), "Options.txt loaded successfully", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to load options.txt: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
            Toast.makeText(requireContext(), "Options.txt saved successfully", Toast.LENGTH_SHORT).show();
            
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Failed to save options.txt: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void undoChanges() {
        if (undoStack.size() > 1) {
            int currentCursorPosition = optionsTextEditor.getSelectionStart();
            String currentText = optionsTextEditor.getText().toString();
            redoStack.push(currentText);
            undoStack.pop(); // Remove current state
            String previousText = undoStack.peek();
            optionsTextEditor.setText(previousText);
            
            // Maintain cursor position or set to safe position
            int safePosition = Math.min(currentCursorPosition, previousText.length());
            optionsTextEditor.setSelection(safePosition);
        } else {
            Toast.makeText(requireContext(), "Nothing to undo", Toast.LENGTH_SHORT).show();
        }
    }

    private void redoChanges() {
        if (!redoStack.isEmpty()) {
            int currentCursorPosition = optionsTextEditor.getSelectionStart();
            String redoText = redoStack.pop();
            undoStack.push(redoText);
            optionsTextEditor.setText(redoText);
            
            // Maintain cursor position or set to safe position
            int safePosition = Math.min(currentCursorPosition, redoText.length());
            optionsTextEditor.setSelection(safePosition);
        } else {
            Toast.makeText(requireContext(), "Nothing to redo", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleSearch() {
        if (searchInputLayout.getVisibility() == View.GONE) {
            searchInputLayout.setVisibility(View.VISIBLE);
            searchEditText.requestFocus();
        } else {
            searchInputLayout.setVisibility(View.GONE);
            // Clear search results and highlighting
            clearSearchResults();
        }
    }

    private void searchInText(String searchTerm) {
        if (searchTerm.isEmpty()) {
            clearSearchResults();
            return;
        }
        
        // Update current search term and find all matches
        currentSearchTerm = searchTerm;
        findAllMatches(searchTerm);
        
        String text = optionsTextEditor.getText().toString();
        SpannableString spannable = new SpannableString(text);
        
        // Highlight all matches
        for (int matchIndex : searchMatches) {
            spannable.setSpan(
                new BackgroundColorSpan(0xFFFFFF00), // Yellow highlight color
                matchIndex,
                matchIndex + searchTerm.length(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }
        
        optionsTextEditor.setText(spannable);
        
        // Reset match index when search term changes
        currentMatchIndex = -1;
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
        // Clear highlighting by resetting text
        String plainText = optionsTextEditor.getText().toString();
        optionsTextEditor.setText(plainText);
    }

    private void closeOptionsEditor() {
        optionsEditorLayout.setVisibility(View.GONE);
        searchInputLayout.setVisibility(View.GONE);
        
        // Re-enable edit button
        editOptionsButton.setEnabled(true);
        editOptionsButton.setText("Edit options.txt");
        
        // Clear undo/redo stacks
        undoStack.clear();
        redoStack.clear();
        
        Toast.makeText(requireContext(), "Editor closed", Toast.LENGTH_SHORT).show();
    }

    private void findNextMatch(String searchTerm) {
        // If search term changed, find all matches first
        if (!searchTerm.equals(currentSearchTerm)) {
            currentSearchTerm = searchTerm;
            findAllMatches(searchTerm);
            currentMatchIndex = -1;
        }
        
        if (searchMatches.isEmpty()) {
            Toast.makeText(requireContext(), "No matches found", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Move to next match
        currentMatchIndex++;
        if (currentMatchIndex >= searchMatches.size()) {
            currentMatchIndex = 0; // Wrap around to first match
        }
        
        int matchPosition = searchMatches.get(currentMatchIndex);
        
        // Select the found text
        optionsTextEditor.setSelection(matchPosition, matchPosition + searchTerm.length());
        optionsTextEditor.requestFocus();
        
        // Scroll to make the selection visible
        scrollToPosition(matchPosition);
        
        // Show current match info
        String message = "Match " + (currentMatchIndex + 1) + " of " + searchMatches.size();
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void scrollToPosition(int position) {
        // Get the layout of the EditText
        android.text.Layout layout = optionsTextEditor.getLayout();
        if (layout != null) {
            // Get the line number for the position
            int line = layout.getLineForOffset(position);
            
            // Get the Y coordinate of the line
            int lineTop = layout.getLineTop(line);
            int lineBottom = layout.getLineBottom(line);
            int lineHeight = lineBottom - lineTop;
            
            // Calculate scroll position to center the line in view
            int editorHeight = optionsTextEditor.getHeight();
            int scrollY = Math.max(0, lineTop - (editorHeight / 2) + (lineHeight / 2));
            
            // Scroll to the calculated position
            optionsTextEditor.scrollTo(0, scrollY);
        }
    }

    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Check media permissions
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Check MANAGE_EXTERNAL_STORAGE
            return Environment.isExternalStorageManager();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ to Android 10 - Check legacy storage permissions
            return ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED ||
                   ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Below Android 6, permissions are granted at install time
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ (API 33+) - Request media permissions
            String[] permissions = {
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            };
            requestPermissions(permissions, 1001);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+) - Request MANAGE_EXTERNAL_STORAGE
            Toast.makeText(requireContext(), "Please grant 'All files access' permission to backup files", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
            startActivity(intent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6+ to Android 10 - Request READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
            String[] permissions = {
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
            requestPermissions(permissions, 1001);
        }
    }

    private void createBackupAtLocation(Uri saveUri, File rootDir) {
        try {
            // Check if source directory exists and has content
            if (!rootDir.exists()) {
                Toast.makeText(requireContext(), "Minecraft data directory not found: " + rootDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            
            File[] files = rootDir.listFiles();
            if (files == null || files.length == 0) {
                Toast.makeText(requireContext(), "No files found to backup in: " + rootDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            
            Toast.makeText(requireContext(), "Creating backup...", Toast.LENGTH_SHORT).show();
            
            // Create backup directly to the chosen location
            try (ZipOutputStream zos = new ZipOutputStream(requireContext().getContentResolver().openOutputStream(saveUri))) {
                zipDirectoryToStream(rootDir, rootDir.getAbsolutePath(), zos);
                Toast.makeText(requireContext(), "Backup saved successfully!", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void backupAndShare(File rootDir) {
        try {
            // Check if source directory exists and has content
            if (!rootDir.exists()) {
                Toast.makeText(requireContext(), "Minecraft data directory not found: " + rootDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            
            File[] files = rootDir.listFiles();
            if (files == null || files.length == 0) {
                Toast.makeText(requireContext(), "No files found to backup in: " + rootDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
                return;
            }
            
            File cacheDir = requireContext().getCacheDir();
            File zipFile = new File(cacheDir, "mojang_backup.zip");
            
            // Delete existing zip file if it exists
            if (zipFile.exists()) {
                zipFile.delete();
            }
            
            Toast.makeText(requireContext(), "Creating backup...", Toast.LENGTH_SHORT).show();
            zipDirectory(rootDir, zipFile);
            
            if (!zipFile.exists() || zipFile.length() == 0) {
                Toast.makeText(requireContext(), "Failed to create backup file", Toast.LENGTH_LONG).show();
                return;
            }
            
            Uri fileUri = FileProvider.getUriForFile(requireContext(), "com.origin.launcher.fileprovider", zipFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/zip");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share Backup Zip"));
            Toast.makeText(requireContext(), "Backup created successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Backup failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace(); // This will help with debugging
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            zipFileRecursive(sourceDir, sourceDir.getAbsolutePath(), zos);
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
        
        // Skip files that can't be read
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
            // Skip files that can't be read, but continue with others
            System.err.println("Skipping file due to error: " + fileToZip.getAbsolutePath() + " - " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean granted = false;
            for (int result : grantResults) {
                if (result == PackageManager.PERMISSION_GRANTED) {
                    granted = true;
                    break;
                }
            }
            
            if (granted) {
                if (currentRootDir != null) {
                    backupAndShare(currentRootDir);
                } else {
                    Toast.makeText(requireContext(), "No Minecraft data found to backup", Toast.LENGTH_LONG).show();
                }
            } else {
                Toast.makeText(requireContext(), "Storage permission is required to backup files", Toast.LENGTH_LONG).show();
            }
        }
    }

    // Improved adapter for folder names with custom styling
    private static class FolderAdapter extends RecyclerView.Adapter<FolderViewHolder> {
        private final List<String> folders;
        FolderAdapter(List<String> folders) { this.folders = folders; }
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
        public int getItemCount() { return folders.size(); }
    }
    
    private static class FolderViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.TextView textView;
        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            textView = itemView.findViewById(R.id.folderNameText);
        }
        void bind(String folderName) {
            textView.setText(folderName);
        }
    }
    
    // Module adapter
    private static class ModuleAdapter extends RecyclerView.Adapter<ModuleViewHolder> {
        private final List<ModuleItem> modules;
        private final ModuleToggleListener toggleListener;
        
        ModuleAdapter(List<ModuleItem> modules, ModuleToggleListener toggleListener) {
            this.modules = modules;
            this.toggleListener = toggleListener;
        }
        
        @NonNull
        @Override
        public ModuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_module, parent, false);
            return new ModuleViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull ModuleViewHolder holder, int position) {
            holder.bind(modules.get(position), toggleListener);
        }
        
        @Override
        public int getItemCount() { return modules.size(); }
    }
    
    private static class ModuleViewHolder extends RecyclerView.ViewHolder {
        private final TextView moduleNameText;
        private final TextView moduleDescriptionText;
        private final MaterialSwitch moduleToggleSwitch;
        
        ModuleViewHolder(@NonNull View itemView) {
            super(itemView);
            moduleNameText = itemView.findViewById(R.id.moduleNameText);
            moduleDescriptionText = itemView.findViewById(R.id.moduleDescriptionText);
            moduleToggleSwitch = itemView.findViewById(R.id.moduleToggleSwitch);
        }
        
        void bind(ModuleItem module, ModuleToggleListener toggleListener) {
            moduleNameText.setText(module.getName());
            moduleDescriptionText.setText(module.getDescription());
            
            // Set switch state without triggering listener
            moduleToggleSwitch.setOnCheckedChangeListener(null);
            moduleToggleSwitch.setChecked(module.isEnabled());
            
            // Set listener after setting state
            moduleToggleSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                module.setEnabled(isChecked);
                if (toggleListener != null) {
                    toggleListener.onToggle(module, isChecked);
                }
            });
        }
    }
}