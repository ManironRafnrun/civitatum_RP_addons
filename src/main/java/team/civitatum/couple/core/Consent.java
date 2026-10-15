package team.civitatum.couple.core;

public enum Consent {
    ASK, TRUSTED, ALLOW, BLOCK;
    public boolean automatic(boolean trusted) {return this==ALLOW || this==TRUSTED && trusted;}
    public String key(){return "consent.civitatum_couple."+name().toLowerCase(java.util.Locale.ROOT);}
}
