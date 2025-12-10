package com.yt.server.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yt.server.entity.UniPoint;
import org.apache.commons.collections.map.MultiValueMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

import static java.lang.System.out;

public class IoUtil {
    private static final Logger logger = LoggerFactory.getLogger(IoUtil.class);

    public static byte[] bytebuffer2ByteArray(ByteBuffer buffer) {
        //重置 limit 和postion 值
        buffer.flip();
        //获取buffer中有效大小
        int len = buffer.limit() - buffer.position();

        byte[] bytes = new byte[len];

        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = buffer.get();

        }

        return bytes;
    }

    /**
     * 字节码->ASCII码
     *
     * @param bt
     */
    public static String byteToASCII(int bt) {
        char[] chars = Character.toChars(bt);
        out.println(new String(chars));
        return new String(chars);

    }

    private static byte[] combineArrays(byte[]... a) {
        int massLength = 0;
        for (byte[] b : a) {
            massLength += b.length;
        }
        byte[] c = new byte[massLength];
        byte[] d;
        int index = 0;
        for (byte[] anA : a) {
            d = anA;
            System.arraycopy(d, 0, c, index, d.length);
            index += d.length;
        }
        return c;
    }

//    public static byte[] subBytes(byte[] src, int begin, int count) {
//        byte[] bs = new byte[count];
//        for (int i = begin; i < begin + count; i++) {
//            bs[i - begin] = src[i];
//        }
//        return bs;
//    }

    /**
     * 取src的count位数据，左边返回取的长度，右边返回文件剩下的字节
     *
     * @param src
     * @param count
     * @return
     */
    public static Pair<byte[], byte[]> subBytesByPair(byte[] src, int count) {
        if (src.length == 0) {
            return null;
        }
        byte[] bs = new byte[count];
        if (src.length - count < 0) {
            return null;
        }
        byte[] right = new byte[src.length - count];
        System.arraycopy(src, 0, bs, 0, count);
        if (src.length - bs.length >= 0)
            System.arraycopy(src, bs.length, right, 0, src.length - bs.length);

        return Pair.of(bs, right);
    }

    public static Pair<byte[], byte[]> subBytesByParamPair(byte[] src, int startPos, int count) {
        if (src.length == 0) {
            return null;
        }
        byte[] bs = new byte[count];
        if (src.length - count < 0) {
            return null;
        }
        byte[] right = new byte[src.length - count];
        System.arraycopy(src, startPos, bs, 0, count);
        if (src.length - bs.length >= 0)
            System.arraycopy(src, bs.length, right, 0, src.length - bs.length);

        return Pair.of(bs, right);
    }

//    public static  byte[] subBytes(byte[] src, int count) {
//        if (src.length == 0) {
//            return null;
//        }
//        byte[] bs = new byte[count];
//        if (src.length - count < 0) {
//            return null;
//        }
//        byte[] right = new byte[src.length - count];
//        System.arraycopy(src, 0, bs, 0, count);
//        if (src.length - bs.length >= 0)
//            System.arraycopy(src, bs.length, right, 0, src.length - bs.length);
//
//        return Pair.of(bs, right);
//    }

    public static byte[] subSpecifiedBytes(byte[] src, int start, int count) {
        if (src.length == 0) {
            return null;
        }
        byte[] bs = new byte[count];
        if (src.length - count < 0) {
            return null;
        }
        System.arraycopy(src, start, bs, 0, count);
        return bs;
    }

    public static String convertByteData(Pair<byte[], byte[]> pair) {
        return new String(pair.getLeft(), 0, pair.getLeft().length);

    }

    public static String convertByteDataBySingle(byte[] bytes) {
        return new String(bytes, 0, bytes.length);

    }

    public static byte[] bitString2ByteArray2(String bitString) {
        int size = bitString.length() / 8;
        byte[] bytes = new byte[size];

        for (int i = 0, total = 0; i < size; i++) {
            byte b = 0x00;
            for (int j = 0; j < 8; j++, total++) {
                b |= (byte) ((bitString.charAt(total) - 48) << (7 - j));
            }
            bytes[i] = b;
        }
        return bytes;
    }


    public static int getByteNumByCode(byte[] code) {
        if (code.length != 1) {
            throw new RuntimeException("字段类型错误");
        }
        final byte value = code[0];
        if (value == 0x10 || value == 0x11 || value == 0x15 || value == 0x16) {
            return 1;
        } else if (value == 0x17 || value == 0x18) {
            return 2;
        } else if (value == 0x13 || value == 0x19 || value == 0x20 || value == 0x22) {
            return 4;
        } else if (value == 0x14 || value == 0x21 || value == 0x23) {
            return 8;
        } else if (value == 0x24) {
            return 81;
        }
        return 0;


    }

    public static byte[] conver2HexToByte(String hex2Str) {
        String[] temp = hex2Str.split(" ");
        byte[] b = new byte[temp.length];
        for (int i = 0; i < b.length; i++) {
            b[i] = Long.valueOf(temp[i], 2).byteValue();
            if (b[i] < 0) {
                out.println("temp=" + temp[i]);

            }
        }
        return b;
    }

    public static int compose(byte... bytes) {
        if (bytes.length == 1) {
            return (bytes[0] & 0xFF);
        } else if (bytes.length == 2) {
            return (bytes[0] << 8) + ((bytes[1] & 0xFF));
        } else if (bytes.length == 3) {
            return ((bytes[0] & 0xFF) << 16) + ((bytes[1] & 0xFF) << 8) + ((bytes[2] & 0xFF));
        } else if (bytes.length == 4) {
            return (bytes[0] << 24) + ((bytes[1] & 0xFF) << 16) + ((bytes[2] & 0xFF) << 8) + ((bytes[3] & 0xFF));
        }
        return 0;
    }

    public static Long composeLong(byte[] by) {
        //第一个字节是最低有效字节
//        long value = 0;
//        for (int i = 0; i < by.length; i++)
//        {
//            value += ((long) by[i] & 0xffL) << (8 * i);
//        }
        long value = 0;
        for (int i = 0; i < by.length; i++) {
            value = (value << 8) + (by[i] & 0xff);
        }
        return value;
    }

    public static byte[] byteMergerAll(byte[]... values) {
        int length_byte = 0;
        for (byte[] value : values) {
            if (value.length == 0) {
                continue;
            }
            length_byte += value.length;
        }
        byte[] all_byte = new byte[length_byte];
        int countLength = 0;
        for (byte[] b : values) {
            System.arraycopy(b, 0, all_byte, countLength, b.length);
            countLength += b.length;
        }
        return all_byte;
    }


    public static <T> T[] arrConcat(T[] first, T[] second) {

        T[] result = Arrays.copyOf(first, first.length + second.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }


    /**
     * 低32位，高32位合并
     * eg: byte[] low = bitString2ByteArray2("00000100000110111011100010110010");
     * final int compose = compose(low);
     * byte[] high = bitString2ByteArray2("00000000100000100000000000000000");
     * final int composeHigh = compose(high);
     * final long l = combineInt2Long(compose, composeHigh);
     */
    public static long combineInt2Long(int low, int high) {
        return ((long) low & 0xFFFFFFFFL) | (((long) high << 32) & 0xFFFFFFFF00000000L);

    }





    public static void appendData2File(MultiValueMap dataList, String fileName) throws IOException {
        OutputStreamWriter out = null;
        FileOutputStream fos = null;
        try {
            File file = new File(fileName);
            if (!file.getParentFile().isDirectory()) {
                file.getParentFile().mkdirs();
            }
            if (!file.exists()) {
                file.createNewFile();
            }
            // 追加写入文件，且利用UTF-8格局
            fos = new FileOutputStream(fileName, true);
            out = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            for (int dataIndex = 0; dataIndex < dataList.size(); dataIndex++) {
                out.write(String.valueOf(dataList.get("v" + dataIndex)));
                out.write("\r\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (null != out) {
                    out.flush();
                    out.close();
                    fos.close();
                }
            } catch (IOException e) {
            }
        }

    }


    /**
     * 对byte[]进行压缩
     *
     * @param
     * @return 压缩后的数据
     */

    public static byte[] compress(byte[] data) {

        out.println("before:" + data.length);

        GZIPOutputStream gzip = null;

        ByteArrayOutputStream baos = null;

        byte[] newData = null;

        try {

            baos = new ByteArrayOutputStream();

            gzip = new GZIPOutputStream(baos);

            gzip.write(data);

            gzip.finish();

            gzip.flush();

            newData = baos.toByteArray();

        } catch (IOException e) {

            e.printStackTrace();

        } finally {

            try {

                gzip.close();

                baos.close();

            } catch (IOException e) {

                e.printStackTrace();

            }

        }

        out.println("after:" + newData.length);

        return newData;

    }


    /**
     * 将对象转化为解析成json格式文件
     *
     * @param obj
     * @param fileName
     */
    public static void generateJson(Object obj, String fileName) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            /**
             * byte[] data = compress(loadFile());
             * String json = new String(Base64.encodeBase64(data));
             */
            byte[] context = mapper.writeValueAsString(obj).getBytes(StandardCharsets.UTF_8);
            FileUtils.writeByteArrayToFile(new File(fileName), context);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static byte[] generateBytes(Object obj) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            /**
             * byte[] data = compress(loadFile());
             * String json = new String(Base64.encodeBase64(data));
             */
            return mapper.writeValueAsString(obj).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    public static byte[] generateCustomBytes(UniPoint uniPoint) {

        ObjectMapper mapper = new ObjectMapper();
        try {
            /**
             * byte[] data = compress(loadFile());
             * String json = new String(Base64.encodeBase64(data));
             */
            return mapper.writeValueAsString(uniPoint).getBytes(StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 方法功能：将字节数组写入到文件中。
     *
     * @return boolean
     */
    public static boolean save2File(String fileName, byte[] msg) {
        OutputStream fos = null;
        try {
            File file = new File(fileName);
            File parent = file.getParentFile();
            boolean bool;
            if ((!parent.exists()) &&
                    (!parent.mkdirs())) {
                return false;
            }
            fos = new FileOutputStream(file);
            fos.write(msg);
            fos.flush();
            return true;
        } catch (FileNotFoundException e) {
            return false;
        } catch (IOException e) {
            File parent;
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static byte[] convertMap2Bytes(Map map) {
        byte[] bt = null;
        try {

            ByteArrayOutputStream os = new ByteArrayOutputStream();

            ObjectOutputStream oos = new ObjectOutputStream(os);

            oos.writeObject(map);

            bt = os.toByteArray();

            oos.close();

            os.close();

        } catch (IOException e) {

            e.printStackTrace();

        }
        return bt;
    }


    /**
     * 注释：short到字节数组的转换！
     *
     * @param
     * @return
     */
    public static byte[] shortToByte(short number) {
        int temp = number;
        byte[] b = new byte[2];
        for (int i = 0; i < b.length; i++) {
            int value = temp & 0xff;
            b[i] = Integer.valueOf(value).byteValue();
//            b[i]=new Integer(temp &0xff).byteValue();
            // 将最低位保存在最低位
            temp = temp >> 8;// 向右移8位
        }
        return b;
    }

    /**
     * 将int数值转换为占四个字节的byte数组，本方法适用于(低位在前，高位在后)的顺序。 和bytesToInt（）配套使用
     *
     * @param value 要转换的int值
     * @return byte数组
     */
    public static byte[] int2BytesLowAhead(int value) {
        byte[] src = new byte[4];
        src[3] = (byte) ((value >> 24) & 0xFF);
        src[2] = (byte) ((value >> 16) & 0xFF);
        src[1] = (byte) ((value >> 8) & 0xFF);
        src[0] = (byte) (value & 0xFF);
        return src;
    }

    /**
     * 将int数值转换为占四个字节的byte数组，本方法适用于(高位在前，低位在后)的顺序。  和bytesToInt2（）配套使用
     */
    public static byte[] intToBytesHighAhead(int value) {
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        return src;
    }

    public static byte[] float2byte(float f) {
        // 把float转换为byte[]
        int fbit = Float.floatToIntBits(f);

        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) {
            b[i] = (byte) (fbit >> (24 - i * 8));
        }

        // 翻转数组
        int len = b.length;
        // 建立一个与源数组元素类型相同的数组
        byte[] dest = new byte[len];
        // 为了防止修改源数组，将源数组拷贝一份副本
        System.arraycopy(b, 0, dest, 0, len);
        byte temp;
        // 将顺位第i个与倒数第i个交换
        for (int i = 0; i < len / 2; ++i) {
            temp = dest[i];
            dest[i] = dest[len - i - 1];
            dest[len - i - 1] = temp;
        }
        return dest;
    }

    public static byte[] double2Bytes(double d) {

        long value = Double.doubleToRawLongBits(d);

        byte[] byteRet = new byte[8];

        for (int i = 0; i < 8; i++) {

            byteRet[i] = (byte) ((value >> 8 * i) & 0xff);

        }

        return byteRet;

    }

    public static byte[] long2byte(long num) {
        byte[] b = new byte[8];
        b[7] = (byte) (num & 0xff);
        b[6] = (byte) ((num >> 8) & 0xff);
        b[5] = (byte) ((num >> 16) & 0xff);
        b[4] = (byte) ((num >> 24) & 0xff);
        b[3] = (byte) ((num >> 32) & 0xff);
        b[2] = (byte) ((num >> 40) & 0xff);
        b[1] = (byte) ((num >> 48) & 0xff);
        b[0] = (byte) ((num >> 56) & 0xff);
        return b;
    }

    public static final int BUFSIZE = 1024 * 8;

    public static void mergeFiles(String outFile, String[] files) {
        logger.info("Merge " + Arrays.toString(files) + " into " + outFile);
        try (FileChannel outChannel = new FileOutputStream(outFile).getChannel()) {
            for (String f : files) {
                File file = new File(f);
                FileChannel fc = new FileInputStream(f).getChannel();
                ByteBuffer bb = ByteBuffer.allocate((int) file.length());
                while (fc.read(bb) != -1) {
                    bb.flip();
                    outChannel.write(bb);
                    bb.clear();
                }
                fc.close();
            }
            logger.info("Merged Successfully!! ");
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }


    //file to byte
    public static byte[] file2Byte(File filePath) {
        byte[] buffer = null;

        try {
            FileInputStream fis = new FileInputStream(filePath);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[(int) filePath.length()];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (IOException e) {
            logger.error("error message {}", e.getMessage());
        }
        return buffer;
    }

    public static void byte2File(byte[] bfile, String filePath) {
        BufferedOutputStream bos = null;
        FileOutputStream fos = null;
        File file = null;
        try {
//            File dir = new File(filePath);
//            if (!dir.exists()) {// 判断文件目录是否存在
//                dir.mkdirs();
//            }
            file = new File(filePath);
            fos = new FileOutputStream(file);
            bos = new BufferedOutputStream(fos);
            bos.write(bfile);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e1) {
                    logger.error(e1.getMessage(), e1);

                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e1) {
                    logger.error(e1.getMessage(), e1);
                }
            }
        }
    }
    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    public static byte[] readInputStream(InputStream inStream) throws Exception {
        ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        boolean var3 = false;

        int len;
        while((len = inStream.read(buffer)) != -1) {
            outSteam.write(buffer, 0, len);
        }

        outSteam.close();
        inStream.close();
        return outSteam.toByteArray();
    }



}

