//package com.yt.server;
//
//import com.sun.jna.Memory;
//import com.sun.jna.Pointer;
//import com.sun.jna.ptr.IntByReference;
//import com.sun.jna.ptr.ShortByReference;
//import com.yt.server.dll.Main;
//import com.yt.server.dll.ZFY_INFO;
//
//import java.nio.charset.StandardCharsets;
//import java.util.Scanner;
//
///**
// * @description:
// * @projectName:yt-java-server
// * @see:com.yt.server
// * @author:赵瑞文
// * @createTime:2024/9/5 16:40
// * @version:1.0
// */
//public class ScannerTest {
//    static Main.MyDll lib = Main.MyDll.INSTANCE;
//    public static void main(String[] args) {
//        Scanner scanner = new Scanner(System.in);
//        while (true) {
//            displayMenu();
//            int choice = getUserChoice(scanner);
//            switch (choice) {
//                case 1:
//                    initDevice();
//                    break;
//                case 2:
//                    authenticate();
//                    break;
//                case 3:
//                    getIdCode();
//                    break;
//                case 4:
//                    getZFYInfo();
//                    break;
//                case 5:
//                    writeZFYInfo(scanner);
//                    break;
//                case 6:
//                    syncDevTime();
//                    break;
//                case 7:
//                    setMSDC();
//                    break;
//                case 8:
//                    readDeviceResolution();
//                    break;
//                case 9:
//                    readDeviceBatteryDumpEnergy();
//                    break;
//                case 10:
//                    writeDeviceResolution(scanner);
//                    break;
//                case 11:
//                    writeVideoSplitValue(scanner);
//                    break;
//                case 12:
//                    readVideoSplitValue();
//                    break;
//                default:
//                    System.out.println("Invalid choice. Please enter a number between 1 and 12.");
//            }
//        }
//    }
//
//    public static void displayMenu() {
//        System.out.println("*********采集站adb命令测试*************");
//        System.out.println("*******1 Init_Device *********");
//        System.out.println("*******2 Authentication *********");
//        System.out.println("*******3 GetIDCode *********");
//        System.out.println("*******4 GetZFYInfo*********");
//        System.out.println("*******5 WriteZFYInfo*********");
//        System.out.println("*******6 SyncDevTime*******************");
//        System.out.println("*******7 SetMSDC*******************");
//        System.out.println("*******8 ReadDeviceResolution*******************");
//        System.out.println("*******9 ReadDeviceBatteryDumpEnergy*******************");
//        System.out.println("*******10 WriteDeviceResolution*******************");
//        System.out.println("*******11 WriteVideoSplitValue*******************");
//        System.out.println("*******12 ReadVideoSplitValue*******************");
//        System.out.println("********************************");
//        System.out.print("Please input the choose number: ");
//    }
//
//    public static int getUserChoice(Scanner scanner) {
//        while (!scanner.hasNextInt()) {
//            System.out.println("Invalid input. Please enter a valid number.");
//            scanner.next(); // consume invalid token
//        }
//        return scanner.nextInt();
//    }
//
//    public static void initDevice() {
//        System.out.println("Initializing device...");
//        // Your implementation here
//    }
//
//    public static void authenticate() {
//        System.out.println("Authenticating...");
//        // Your implementation here
//    }
//
//    public static void getIdCode() {
//        Memory idCodeMemory = new Memory(256);
//        ShortByReference retRef = new ShortByReference();
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
//
//    public static void getZFYInfo() {
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
//    }
//
//    public static void writeZFYInfo(Scanner scanner) {
//        ZFY_INFO.ByReference info = new ZFY_INFO.ByReference();
//        // 填充结构体字段，例如：
//        System.out.print("Please input serialNo: ");
//        String serialNo = scanner.next();
//
//        System.out.print("Please input userNo: ");
//        String userNo = scanner.next();
//
//        System.out.print("Please input userName: ");
//        String userName = scanner.next();
//
//        System.out.print("Please input unitNo: ");
//        String unitNo = scanner.next();
//
//        System.out.print("Please input unitName: ");
//        String unitName = scanner.next();
//        //封装的set方法当写入大于对应的字节数时候，多余的不写入，反之，不够对应的字节数，是多少就是多少
//        info.setSerial(serialNo);
//        info.setUserNo(userNo);
//        info.setUserName(userName);
//        info.setUnitNo(unitNo);
//        info.setUnitName(unitName);
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
//    public static void syncDevTime() {
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
//    public static void setMSDC() {
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
//    public static void readDeviceResolution() {
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
//    public static void readDeviceBatteryDumpEnergy() {
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
//    public static void writeDeviceResolution(Scanner scanner) {
//        ShortByReference retRef = new ShortByReference();
//        String password = "123456";
//        byte[] sPwd = password.getBytes(StandardCharsets.UTF_8);
//        // 确保 sPwd 数组长度符合要求，不足部分用 0 填充
//        if (sPwd.length < 16) {
//            byte[] temp = new byte[16];
//            System.arraycopy(sPwd, 0, temp, 0, sPwd.length);
//            sPwd = temp;
//        }
//        System.out.print("Please input Width: ");
//        int width = getUserChoice(scanner);
//        System.out.print("Please input Height: ");
//        int height = getUserChoice(scanner);
//        int result = lib.WriteDeviceResolution(width, height, sPwd, retRef);
//        if (result == 1) { //
//            // 将内存中的数据转换为字符串
//            System.out.println("write success");
//            System.out.println("response Code: " + retRef.getValue());
//        } else {
//            System.out.println("Failed to WriteDeviceResolution");
//        }
//    }
//
//    public static void writeVideoSplitValue(Scanner scanner) {
//        ShortByReference iRetRef = new ShortByReference();
//        System.out.print("Please input split: ");
//        int splitValue = getUserChoice(scanner);
//        // 设置分割值
//        //int splitValue = 33;
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
//    public static void readVideoSplitValue() {
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
//}
