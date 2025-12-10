package com.yt.server;

import java.util.Arrays;

/**
 * @description:
 * @projectName:yt-java-server
 * @see:com.yt.server
 * @author:赵瑞文
 * @createTime:2024/9/10 17:21
 * @version:1.0
 */
public class ByteWrapper {

    private final Byte[] data;

    public ByteWrapper(Byte[] data) {
        this.data = Arrays.copyOf(data, data.length); // 防止外部修改
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        ByteWrapper that = (ByteWrapper) obj;
        return Arrays.equals(this.data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return Arrays.toString(this.data);
    }
}
