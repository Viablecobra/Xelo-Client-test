package org.levimc.launcher.core.auth.storage;

import android.content.Context;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;

import org.levimc.launcher.core.auth.AuthConfig;

public class TitleDefaultStore implements XalJsonExportable {

    public static class DefaultBox {
        @SerializedName("default")
        public String defaultUserId;
    }

    private static final Gson GSON = new Gson();
    private final DefaultBox box;
    private final String filename;

    private TitleDefaultStore(DefaultBox box, String filename) {
        this.box = box;
        this.filename = filename;
    }

    public static String filename(AuthConfig config) {
        String tid = config.defaultTitleTid();
        return "Xal." + tid + ".Production.Default.json";
    }

    public static void save(Context ctx, String userId) {
        DefaultBox box = new DefaultBox();
        box.defaultUserId = userId;
        String json = GSON.toJson(box);
        File f = XalStorageManager.getTitleDefaultFile(ctx, userId);
        org.levimc.launcher.util.JsonIOUtils.write(f, json);
    }


    @Override
    public String filename() {
        return filename;
    }

    @Override
    public String toJson() {
        return GSON.toJson(box);
    }
}