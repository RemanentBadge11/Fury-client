package com.lionclient.command;

import java.util.ArrayList;
import java.util.List;

public abstract class Command {
    private final List<String> names;

    public Command(List<String> names) {
        this.names = names;
    }

    public List<String> getNames() {
        return names;
    }

    public abstract void runCommand(ArrayList<String> args);
}
