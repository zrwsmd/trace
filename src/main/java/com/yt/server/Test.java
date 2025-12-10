//package com.yt.server;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server
// * @author:赵瑞文
// * @createTime:2024/8/23 9:32
// * @version:1.0
// */
//
//import com.sun.jna.Native;
//import com.sun.jna.Pointer;
//import com.sun.jna.ptr.ShortByReference;
//import com.sun.jna.win32.StdCallLibrary;
//
//import java.nio.charset.StandardCharsets;
//
//public class Test {
//    // 接口定义
//    interface MyDll extends StdCallLibrary {
//        String MY_DLL_PATH = "D:\\Documents\\WeChat Files\\wxid_chyxeiwg3hv922\\FileStorage\\File\\2024-08\\testDll\\dll64.dll";
//        // NativeLibrary.addSearchPath(MyDll.class.getName(), MyDll.MY_DLL_PATH);
//        MyDll INSTANCE = (MyDll) Native.loadLibrary(MY_DLL_PATH, MyDll.class);
//        //int GetIDCode(Pointer idCode, ShortByReference iRet);
//        int GetIDCode(Pointer idCode, ShortByReference iRet);
//
//    }
//
//    public static void main(String[] args) {
//        IDCodeStruct idCodeStruct = new IDCodeStruct();
//        Pointer idCodePointer = new Memory(idCodeStruct.size()).getPointer();
//        ShortByReference iRetRef = new ShortByReference();
//
//        // 调用 GetIDCode 方法
//        int result = library.GetIDCode(idCodePointer, iRetRef);
//
//        if (result == 1) { // 成功获取识别码
//            // 从 Pointer 中读取数据到 idCodeStruct
//            idCodeStruct.read(idCodePointer);
//            System.out.println("ID Code: " + idCodeStruct.getIDCodeAsString());
//            System.out.println("Return Code: " + iRetRef.getValue());
//        } else {
//            System.out.println("Failed to get ID code.");
//        }
//    }
//
//    //    public static void main(String[] args) {
////        ShortByReference iRet = new ShortByReference((short) 15);
////        System.out.println("Initial value: " + iRet.getValue()); // 输出初始值
////
////        // 修改值
////        iRet.setValue((short) 20);
////        System.out.println("Updated value: " + iRet.getValue()); // 输出更新后的值
////    }
//}
