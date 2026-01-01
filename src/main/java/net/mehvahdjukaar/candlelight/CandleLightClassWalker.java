package net.mehvahdjukaar.candlelight;


import org.gradle.api.Project;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

import static org.objectweb.asm.Opcodes.*;

public class CandleLightClassWalker {

    public static void walkClasses(File dir, ClassFileConsumer c) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) walkClasses(f, c);
            else if (f.getName().endsWith(".class")) c.accept(f);
        }
    }

    public static byte[] readAllBytes(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return in.readAllBytes();
        }
    }

    public interface ClassFileConsumer {
        void accept(File f) throws IOException;
    }

    public static int getReturnOpcode(String desc) {
        return switch (Type.getReturnType(desc).getSort()) {
            case Type.VOID -> RETURN;
            case Type.BOOLEAN, Type.BYTE, Type.CHAR, Type.SHORT, Type.INT -> IRETURN;
            case Type.LONG -> LRETURN;
            case Type.FLOAT -> FRETURN;
            case Type.DOUBLE -> DRETURN;
            default -> ARETURN;
        };
    }

}
