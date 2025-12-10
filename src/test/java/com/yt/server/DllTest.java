//package com.yt.server;
//
//import com.sun.jna.Memory;
//import com.sun.jna.Pointer;
//import com.sun.jna.ptr.ByteByReference;
//import com.sun.jna.ptr.IntByReference;
//import com.sun.jna.ptr.PointerByReference;
//import com.sun.jna.ptr.ShortByReference;
//import com.yt.server.dll.Main;
//import com.yt.server.dll.ZFYInfoStruct;
//import com.yt.server.dll.ZFY_INFO;
//import org.junit.jupiter.api.Test;
//import com.sun.jna.Native;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Arrays;
//
//
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server
// * @author:赵瑞文
// * @createTime:2024/8/27 10:07
// * @version:1.0
// */
//public class DllTest {
//    Main.MyDll lib = Main.MyDll.INSTANCE;
//
//    @Test
//    void testGetIDCode() {
//        getIdCode();
//    }
//
//
//    @Test
//    void testInit_Device() {
//        getIdCode();
//        // 创建一个足够大的内存空间来存储 IDCode，假设最大长度为 255 字节加上结尾的空字符
//        Memory idCodeMemory = new Memory(256);
//        ShortByReference retRef = new ShortByReference();
//        // 调用 GetIDCode 方法
//        int result = lib.Init_Device(idCodeMemory, retRef);
//        if (result == 1) { //
//            System.out.println("init device success");
//        } else {
//            System.out.println("Failed to init device");
//        }
//    }
//
//    @Test
//    void testWriteDeviceResolution() {
//        getIdCode();
//        // Memory idCodeMemory = new Memory(256);
//        ShortByReference retRef = new ShortByReference();
//        String password = "123456";
//        byte[] sPwd = password.getBytes(StandardCharsets.UTF_8);
//        // 确保 sPwd 数组长度符合要求，不足部分用 0 填充
//        if (sPwd.length < 16) {
//            byte[] temp = new byte[16];
//            System.arraycopy(sPwd, 0, temp, 0, sPwd.length);
//            sPwd = temp;
//        }
//        int result = lib.WriteDeviceResolution(88, 88, sPwd, retRef);
//        if (result == 1) { //
//            // 将内存中的数据转换为字符串
//            System.out.println("write success");
//            System.out.println("response Code: " + retRef.getValue());
//        } else {
//            System.out.println("Failed to WriteDeviceResolution");
//        }
//    }
//
//    @Test
//    void testWriteZFYInfo() {
//        getIdCode();
//        ZFY_INFO.ByReference info = new ZFY_INFO.ByReference();
//        // 填充结构体字段，例如：
////        info.cSerial = "1234567".getBytes();
////        info.userNo = "777777".getBytes();
////        info.userName = "qwerqwerqwerqwerqwerqwerqwerqwer".getBytes();
////        info.unitNo = "888888888888".getBytes();
////        info.unitName = "opeq931opeq931opeq931opeq931opeq".getBytes();
//        //封装的set方法当写入大于对应的字节数时候，多余的不写入，反之，不够对应的字节数，是多少就是多少
//        info.setSerial("00303");
//        info.setUserNo("888888");
//        info.setUserName("qwerqwerqwerqwe");
//        info.setUnitNo("999999999999");
//        info.setUnitName("opeq931opeq931opeq931opeq931opeq");
//        // 将结构体的内存地址传递给 DLL 函数
//        Pointer infoPtr = info.getPointer();
//        // 设置 sPwd 密码
//        String sPwd = "123456";
//        // 创建 ShortByReference 实例，用于接收 iRet 的值
//        ShortByReference iRetRef = new ShortByReference();
//        // 调用 WriteZFYInfo 方法
//        int result = lib.WriteZFYInfo(info, sPwd, iRetRef);
//        // 检查结果
//        if (result == 1) { // 假设返回值为 1 表示成功
//            System.out.println("WriteZFYInfo succeeded, iRet: " + iRetRef.getValue());
//        } else {
//            System.out.println("WriteZFYInfo failed, iRet: " + iRetRef.getValue());
//        }
//    }
//
//    @Test
//    void testGetZFYInfo() {
//        getIdCode();
//        ZFY_INFO.ByReference infoRef = new ZFY_INFO.ByReference();
//        ShortByReference iRet = new ShortByReference();
//        String password = "123456";
//        // 调用 native 函数
//        int result = lib.INSTANCE.GetZFYInfo(infoRef, password, iRet);
//        // 检查结果
//        if (result == 1) {
//            // 成功获取信息
//            String serial = infoRef.getCSerialAsString();
//            String userNo = infoRef.getUserNoAsString();
//            String userName = infoRef.getUserNameAsString();
//            String unitNo = infoRef.getUnitNoAsString();
//            String unitName = infoRef.getUnitNameAsString();
//            System.out.println("Serial: " + serial);
//            System.out.println("UserNo: " + userNo);
//            System.out.println("UserName: " + userName);
//            System.out.println("UnitNo: " + unitNo);
//            System.out.println("UnitName: " + unitName);
//        } else {
//            // 获取信息失败
//            System.out.println("Failed to get ZFY info. Result: " + result + ", iRet: " + iRet.getValue());
//        }
//
//    }
//
//    @Test
//    void testReadVideoSplitValue() {
//        getIdCode();
//        // 创建 IntByReference 实例，用于接收 split 值
//        IntByReference splitRef = new IntByReference();
//        Memory splitMemory = new Memory(256);
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        String sPwd = "123456";
//        // 调用 ReadVideoSplitValue 方法
//        int result = lib.ReadVideoSplitValue(splitMemory, sPwd, iRetRef);
//        if (result == 1) {
//            // 调用成功，处理返回的 split 值和 iRet 错误代码
//            int splitValue = splitMemory.getInt(0);
//            short iRetValue = iRetRef.getValue();
//            System.out.println("Split value read successfully: " + splitValue);
//            System.out.println("Return code: " + iRetValue);
//        } else {
//            // 调用失败，处理错误
//            System.out.println("Failed to read split value.");
//        }
//    }
//
//    @Test
//    void testReadDeviceResolution() {
//       getIdCode();
//       // readIDCodeLocal();
//        // 创建 IntByReference 实例，用于接收 Width 和 Height 的值
//        IntByReference widthRef = new IntByReference();
//        IntByReference heightRef = new IntByReference();
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        String sPwd = "123456";
//        // 调用 ReadDeviceResolution 方法
//        int result = lib.ReadDeviceResolution(widthRef, heightRef, sPwd, iRetRef);
//        if (result == 1) { // 假设返回值为 1 表示成功
//            // 获取 Width 和 Height 的值
//            int width = widthRef.getValue();
//            int height = heightRef.getValue();
//            System.out.println("Device Resolution: Width=" + width + ", Height=" + height);
//            System.out.println("Return Code: " + iRetRef.getValue());
//        } else {
//            System.out.println("Failed to read device resolution.");
//        }
//    }
//
//    @Test
//    void testSetMSDC() {
//        getIdCode();
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        String sPwd = "123456";
//        // 调用 SetMSDC 方法
//        int result = lib.SetMSDC(sPwd, iRetRef);
//        // 检查返回值来确定操作是否成功
//        if (result == 1) { // 假设返回值为 1 表示成功
//            System.out.println("SetMSDC succeeded with return code: " + iRetRef.getValue());
//        } else {
//            System.out.println("SetMSDC failed with return code: " + iRetRef.getValue());
//        }
//    }
//
//    @Test
//    void testSyncDevTime() {
//        getIdCode();
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        String sPwd = "123456";
//        // 调用 SyncDevTime 方法
//        int result = lib.SyncDevTime(sPwd, iRetRef);
//        // 检查返回值来确定操作是否成功
//        if (result == 1) {
//            System.out.println("Device time synchronized successfully with return code: " + iRetRef.getValue());
//        } else {
//            System.out.println("Failed to synchronize device time with return code: " + iRetRef.getValue());
//        }
//    }
//
//    @Test
//    void testReadDeviceBatteryDumpEnergy() {
//        getIdCode();
//        // 创建 IntByReference 实例，用于接收电池电量的值
//        IntByReference batteryRef = new IntByReference();
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        // 设置密码字符串
//        String sPwd = "123456";
//        // 调用 ReadDeviceBatteryDumpEnergy 方法
//        int result = lib.ReadDeviceBatteryDumpEnergy(batteryRef, sPwd, iRetRef);
//        if (result == 1) {
//            int batteryLevel = batteryRef.getValue();
//            System.out.println("Device battery level read successfully,value is: " + batteryLevel + "%");
//            System.out.println("Return Code: " + iRetRef.getValue());
//        } else {
//            System.out.println("Failed to read device battery level with return code: " + iRetRef.getValue());
//        }
//    }
//
//    @Test
//    void testWriteVideoSplitValue() {
//        getIdCode();
//        // 创建 ShortByReference 实例，用于接收错误代码
//        ShortByReference iRetRef = new ShortByReference();
//        // 设置分割值
//        int splitValue = 55;
//        // 设置密码字符串
//        String sPwd = "123456";
//        // 调用 WriteVideoSplitValue 方法
//        int result = lib.WriteVideoSplitValue(splitValue, sPwd, iRetRef);
//        // 检查返回值来确定操作是否成功
//        if (result == 1) { // 返回 true 表示成功
//            System.out.println("Video split value written successfully with return code: " + iRetRef.getValue());
//        } else {
//            System.out.println("Failed to write video split value with return code: " + iRetRef.getValue());
//        }
//    }
//
//
//    private void getIdCode() {
//        Memory idCodeMemory = new Memory(256);
//        ShortByReference retRef = new ShortByReference();
//        // 调用 GetIDCode 方法
//        int result = lib.GetIDCode(idCodeMemory, retRef);
//        if (result == 1) { //
//            // 将内存中的数据转换为字符串
//            String idCode = idCodeMemory.getString(0);
//            System.out.println("IDCode: " + idCode);
//            System.out.println("response Code: " + retRef.getValue());
//        } else {
//            System.out.println("Failed to get IDCode");
//        }
//        System.out.println("---------------------------------------------------");
//    }
//}
