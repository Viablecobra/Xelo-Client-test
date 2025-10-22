package com.microsoft.xal.logging;



public class LogEntry {
    private final XalLogger.LogLevel m_level;
    private final String m_message;

    public LogEntry(XalLogger.LogLevel level, String message) {
        m_level = level;
        m_message = message;
    }

    public String Message() {
        return m_message;
    }

    public int Level() {
        return m_level.ToInt();
    }
}