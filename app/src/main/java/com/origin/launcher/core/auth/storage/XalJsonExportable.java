package org.levimc.launcher.core.auth.storage;

public interface XalJsonExportable {
    String filename();

    String toJson();
}