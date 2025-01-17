package io.github.jark006.freezeit;


import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.FileUtils;
import android.provider.OpenableColumns;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.Set;
import io.github.jark006.freezeit.R;
public class Utils {
    public final static int CFG_TERMINATE = 10,
            CFG_SIGSTOP = 20,
            CFG_SIGSTOP_BR = 21, //附加断网
            CFG_FREEZER = 30,
            CFG_FREEZER_BR = 31, //附加断网
            CFG_WHITELIST = 40,
            CFG_WHITEFORCE = 50;
    public final static Set<Integer> CFG_SET = Set.of(
            CFG_TERMINATE,
            CFG_SIGSTOP,
            CFG_SIGSTOP_BR,
            CFG_FREEZER,
            CFG_FREEZER_BR,
            CFG_WHITELIST,
            CFG_WHITEFORCE
    );

    private static final String TAG = "Freezeit[Utils]";

    // 获取信息 无附加数据 No additional data required
    public final static byte getPropInfo = 2;     // return string: "ID\nName\nVersion\nVersionCode\nAuthor\nClusterNum"
    public final static byte getChangelog = 3;    // return string: "changelog"
    public final static byte getLog = 4;          // return string: "log"
    public final static byte getAppCfg = 5;       // return string: "uid cfg isTolerant\n..."   "包名 配置 宽松\n..." <uid, <cfg, isTolerant>>
    public final static byte getRealTimeInfo = 6; // return bytes[rawBitmap+String]: (rawBitmap+内存 频率 使用率 电流)
    public final static byte getSettings = 8;     // return bytes[256]: all settings parameter
    public final static byte getUidTime = 9;      // return "uid x x x x\n..."

    // 设置 需附加数据
    public final static byte setAppCfg = 21;      // send "uid cfg isTolerant\n..." see CMD:getAppCfg
    public final static byte setAppLabel = 22;    // send "uid label\n..."
    public final static byte setSettingsVar = 23; // send bytes[2]: [0]index [1]value

    // 其他命令 无附加数据 No additional data required
    public final static byte clearLog = 61;         // return string: "log" //清理并返回log
    public final static byte printFreezerProc = 62; // return string: "log" //打印冻结状态进程并返回log

    public static synchronized int freezeitTask(byte command, byte[] AdditionalData) {
        // dataHeader[0-3]:附带数据大小(uint32 小端)
        // dataHeader[4]: 命令(可参考上面)
        // dataHeader[5]: 附带数据的异或校验值
        byte[] dataHeader = {0, 0, 0, 0, command, 0};
        try {
            var socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1", 60613), 3000);
            InputStream is = socket.getInputStream();
            OutputStream os = socket.getOutputStream();

            if (AdditionalData != null && AdditionalData.length > 0) {
                byte XOR = 0;
                for (byte b : AdditionalData)
                    XOR ^= b;

                Int2Byte(AdditionalData.length, dataHeader, 0);
                dataHeader[5] = XOR;

                os.write(dataHeader);
                os.write(AdditionalData);
            } else {
                os.write(dataHeader);
            }

            os.flush();

            int receiveLen = is.read(dataHeader, 0, 6);
            if (receiveLen != 6) {
                Log.e(TAG, "Receive dataHeader Fail, receiveLen:" + receiveLen);
                socket.close();
                return 0;
            }

            final int payloadLen = Byte2Int(dataHeader, 0);
            if (StaticData.response.length < payloadLen)
                StaticData.response = new byte[payloadLen];
            int readCnt = 0;
            while (readCnt < payloadLen) { //欲求不满
                int cnt = is.read(StaticData.response, readCnt, payloadLen - readCnt);
                if (cnt < 0) {
                    Log.e(TAG, "Get payload Fail");
                    socket.close();
                    return 0;
                }
                readCnt += cnt;
            }
            socket.close();

            return payloadLen;
        } catch (IOException e) {
            return 0;
        }
    }

    public static byte[] getNetworkData(String link) {
        try {
            URL url = new URL(link);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5 * 1000);
            conn.setRequestMethod("GET");
            conn.connect();
            if (conn.getResponseCode() != 200) return null;

            InputStream is = conn.getInputStream();
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int len;
            byte[] buffer = new byte[4096];
            while ((len = is.read(buffer)) != -1)
                os.write(buffer, 0, len);

            is.close();
            os.close();
            return os.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    public static void imgDialog(Context context, @DrawableRes int drawableID) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(R.layout.img_dialog);
        ((ImageView) dialog.findViewById(R.id.img)).setImageResource(drawableID);
        dialog.show();
    }

    public static void layoutDialog(Context context, @LayoutRes int layoutId) {
        Dialog dialog = new Dialog(context);
        dialog.setContentView(layoutId);
        dialog.show();
    }

    public static void textDialog(Context context, int titleResID, int contentResID) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(titleResID).setMessage(contentResID).create().show();
    }

    public static Bitmap resize(Bitmap bitmap, float scale) {
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, false);
    }

    // 小端 只是避免内存越界，不处理转换失败的情况
    public static int Byte2Int(byte[] bytes, int byteOffset) {
        if (bytes == null || (byteOffset + 4) > bytes.length)
            return 0;

        return Byte.toUnsignedInt(bytes[byteOffset]) |
                (Byte.toUnsignedInt(bytes[byteOffset + 1]) << 8) |
                (Byte.toUnsignedInt(bytes[byteOffset + 2]) << 16) |
                (Byte.toUnsignedInt(bytes[byteOffset + 3]) << 24);
    }

    public static void Int2Byte(int value, byte[] bytes, int byteOffset) {
        if (bytes == null) return;
        if ((byteOffset + 4) > bytes.length) {
            while (byteOffset < bytes.length)
                bytes[byteOffset++] = 0;
            return;
        }

        bytes[byteOffset++] = (byte) value;
        bytes[byteOffset++] = (byte) (value >> 8);
        bytes[byteOffset++] = (byte) (value >> 16);
        bytes[byteOffset] = (byte) (value >> 24);
    }

    public static void Byte2Int(byte[] bytes, int byteOffset, int byteLength, int[] ints, int intOffset) {
        if (ints == null || bytes == null || (intOffset + byteLength / 4) > ints.length ||
                (byteOffset + byteLength) > bytes.length)
            return;

        for (int byteIdx = byteOffset; byteIdx < byteOffset + byteLength; byteIdx += 4) {
            ints[intOffset++] = Byte.toUnsignedInt(bytes[byteIdx]) |
                    (Byte.toUnsignedInt(bytes[byteIdx + 1]) << 8) |
                    (Byte.toUnsignedInt(bytes[byteIdx + 2]) << 16) |
                    (Byte.toUnsignedInt(bytes[byteIdx + 3]) << 24);
        }
    }

    public static void Int2Byte(int[] ints, int intOffset, int intLength, byte[] bytes, int byteOffset) {
        if (ints == null || bytes == null || (intOffset + intLength) > ints.length ||
                (byteOffset + intLength * 4) > bytes.length)
            return;

        for (int intIdx = intOffset; intIdx < intOffset + intLength; intIdx++) {
            bytes[byteOffset++] = (byte) ints[intIdx];
            bytes[byteOffset++] = (byte) (ints[intIdx] >> 8);
            bytes[byteOffset++] = (byte) (ints[intIdx] >> 16);
            bytes[byteOffset++] = (byte) (ints[intIdx] >> 24);
        }
    }

    public static String getFileAbsolutePath(Context context, Uri uri) {
        if (context == null || uri == null)
            return null;

        File file = null;
        //android10以上转换
        if (uri.getScheme().equals(ContentResolver.SCHEME_FILE)) {
            file = new File(uri.getPath());
        } else if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            //把文件复制到沙盒目录
            ContentResolver contentResolver = context.getContentResolver();
            @SuppressLint("Recycle")
            Cursor cursor = contentResolver.query(uri, null, null, null, null);
            if (cursor.moveToFirst()) {
                @SuppressLint("Range")
                String displayName = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                try {
                    InputStream is = contentResolver.openInputStream(uri);
                    File cache = new File(context.getExternalCacheDir().getAbsolutePath(), Math.round((Math.random() + 1) * 1000) + displayName);
                    FileOutputStream fos = new FileOutputStream(cache);
                    FileUtils.copy(is, fos);
                    file = cache;
                    fos.close();
                    is.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return file == null ? null : file.getAbsolutePath();
    }
}
