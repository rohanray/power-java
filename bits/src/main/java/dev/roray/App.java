package dev.roray;

import org.openjdk.jol.info.ClassLayout;
import org.openjdk.jol.vm.VM;

class BoolWrapper{
private    boolean value;

}

public class App {
    public static void main(String[] args) {
        IO.println("Hello World!");
        IO.println(VM.current().details());
        IO.println(ClassLayout.parseClass(BoolWrapper.class).toPrintable());
    }
}
