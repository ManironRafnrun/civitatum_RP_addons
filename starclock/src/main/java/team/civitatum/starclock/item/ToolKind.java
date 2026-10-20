package team.civitatum.starclock.item;

public enum ToolKind {
    DECK("deck"), SEXTANT("sextant"), BOWL("bowl"), RING("ring");

    private final String id;

    ToolKind(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
