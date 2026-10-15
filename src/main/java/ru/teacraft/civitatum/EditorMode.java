package ru.teacraft.civitatum;

public enum EditorMode {
    CREATE, FULL, COSMETIC;
    public boolean body() { return this!=COSMETIC; }
    public boolean race() { return this==CREATE; }
}
