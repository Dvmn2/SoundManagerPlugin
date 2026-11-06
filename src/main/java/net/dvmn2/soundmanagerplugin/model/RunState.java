package net.dvmn2.soundmanagerplugin.model;

/** State of a playlist run. The {@code id} is the value sent to the mod. */
public enum RunState {
    STOPPED(0), PLAYING(1), PAUSED(2);

    private final int id;

    RunState(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }
}
