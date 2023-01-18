package io.github.jark006.freezeit;

public class StaticData {

    public static boolean hasGetUpdateInfo = false;
    public static int onlineVersionCode = 0;
    public static String onlineVersion = "";
    public static String onlineChangelog = "";
    public static String zipUrl = "";
    public static String changelogUrl = "";

    public static boolean hasGetPropInfo = false;
    public static int clusterNum = 0;
    public static int moduleVersionCode = 0;
    public static String moduleVersion = "";
    public static String moduleEnv = "";      // Magisk or KernelSU
    public static String localChangelog = ""; // 当前模块版本自带的本地更新日志

    // 图像宽/高均缩小为控件的 1/imgScale, 减少绘图花销
    // 显示到imageView控件时再放大 imgScale 倍
    public static final int imgScale = 3;
    public static int imgWidth = 0;
    public static int imgHeight = 0;
}