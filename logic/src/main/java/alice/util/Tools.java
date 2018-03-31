/*
 *   Tools.java
 *
 * Copyright 2000-2001-2002  aliCE team at deis.unibo.it
 *
 * This software is the proprietary information of deis.unibo.it
 * Use is subject to license terms.
 *
 */
package alice.util;

import java.io.FileInputStream;
import java.io.IOException;

/**
 *
 * miscellaneous static services
 *
 */
public class Tools {

    
    /**
     * loads a text file and returns its
     * content as string
     */
    public static String loadText(String fileName) throws IOException {
        //FileInputStream is=new FileInputStream(fileName);
        try {
            return new String(ClassLoader.getSystemResourceAsStream(fileName).readAllBytes());
        } catch (Exception ex){
        }
        // resource not found among system resources: try as a file
        try {
            return new String(new FileInputStream(fileName).readAllBytes());
        } catch (Exception ex){
        }
        throw new IOException("File not found.");
    }

//    /**
//     * loads a text file from an InputStream
//     */
//    public static String loadText(InputStream is) throws IOException {
//        byte[] info=new byte[is.available()];
//        is.read(info);
//        return new String(info);
//    }
//
//    /**
//     * give a command line argument list, this method gets the option
//     * of the specified prefix
//     */
//    public static String getOpt(String[] args,String prefix){
//        for (int i=0; i<args.length; i++)
//            if (args[i].equals(prefix)){
//                return args[i+1];
//            }
//        return null;
//    }
//
//    /**
//     * give a command line argument list, this method tests for
//     * the presence of the option of the specified prefix
//     */
//    public static boolean isOpt(String[] args,String prefix){
//        for (int i=0; i<args.length; i++)
//            if (args[i].equals(prefix)){
//                return true;
//            }
//        return false;
//    }
    
    public static String removeApostrophes(String st){
        return st.startsWith("'") && st.endsWith("'") ? st.substring(1, st.length() - 1) : st;
    }
}
