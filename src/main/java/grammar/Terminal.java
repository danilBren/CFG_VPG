package grammar;

public class Terminal extends Node {

    public boolean isNotSet = false;

    public Terminal(String label) {
        super(label);
    }

    @Override
    public String toString() {
        return super.getName();
    }
}
