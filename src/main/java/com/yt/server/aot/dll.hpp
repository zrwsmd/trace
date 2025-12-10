#ifndef DLLDLL
#define DLLDLL

#define EXPORT __declspec(dllexport)

extern "C"
{
    typedef struct
    {
        char cSerial[7];   /*供电服务记录仪产品序号，不可为空*/
        char userNo[6];    /*供电服务记录仪使用者编号，不可为空*/
        char userName[32]; /*供电服务记录仪使用者姓名，管理平台使用编号关联时可为空*/
        char unitNo[12];   /*供电服务记录仪使用者单位编号，管理平台使用编号关联时可为空*/
        char unitName[32]; /*供电服务记录仪使用者单位名称，管理平台使用编号关联时可为空*/
    }
    ZFY_INFO;

    typedef enum _RESULT_FLAG
    {
        CONNECT_SUCCESS = 0x01, /*供电服务记录仪连接成功*/
        CONNECT_FAILED,         /*供电服务记录仪连接失败*/
        CHECK_PWD_SUCCESS,      /*供电服务记录仪管理员密码校验成功*/
        CHECK_PWD_FAILED,       /*供电服务记录仪管理员密码校验失败*/
        SET_SYSTEMTIME_SUCCESS, /*供电服务记录仪系统时间同步成功*/
        SET_SYSTEMTIME_FAILED,  /*供电服务记录仪系统时间同步失败*/
        MSDC_SUCCESS,           /*供电服务记录仪转换移动磁盘模式成功*/
        MSDC_FAILED             /*供电服务记录仪转换移动磁盘模式失败*/
    } RESULT_FLAG;

    // @brief: 初始化连接
    // @param: IDCode 识别码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否连接成功 布尔型 ture or false
    EXPORT int Init_Device(char * IDCode, short * iRet);

    // @brief: 身份认证
    // @param: SessionData 认证数据 字符串
    // @param: MAC 校验信息 无符号短整型
    // @ret: 无符号整形
    EXPORT unsigned int Authentication(char * SessionData, char * MAC);

    // @brief: 获取生产厂代码及产品型号代码
    // @param: IDCode 识别码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功获取识别码 布尔型 ture or false
    EXPORT int GetIDCode(char * IDCode, short * iRet);

    // @brief: 获取记录仪信息
    // @param: info 记录仪信息 ZFY_INFO
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功获取记录仪信息 布尔型 ture or false
    EXPORT int GetZFYInfo(ZFY_INFO * info, char * sPwd, short * iRet);

    // @brief: 写入记录仪信息
    // @param: info 记录仪信息 ZFY_INFO
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功写入记录仪信息 布尔型 ture or false
    EXPORT int WriteZFYInfo(ZFY_INFO * info, char * sPwd, short * iRet);

    // @brief: 同步记录仪时间
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功同步记录仪时间 布尔型 ture or false
    EXPORT int SyncDevTime(char * sPwd, short * iRet);

    // @brief: 设置为移动磁盘模式
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功设置为移动磁盘 布尔型 ture or false
    EXPORT int SetMSDC(char * sPwd, short * iRet);

    // @brief: 读取当前摄录分辨率
    // @param: Width 分辨率宽 整型指针
    // @param: Height 分辨率高 整型指针
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功获取到摄录分辨率 布尔型 ture or false
    EXPORT int ReadDeviceResolution(int * Width, int * Height, char * sPwd, short * iRet);

    // @brief: 读取当前摄录分辨率
    // @param: Battery 电量百分比值×100 取值范围: 0~100 整型指针
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功获取电量 布尔型 ture or false
    EXPORT int ReadDeviceBatteryDumpEnergy(int * Battery, char * sPwd, short * iRet);

    // @brief: 设置当前摄录分辨率
    // @param: Width 分辨率宽
    // @param: Height 分辨率高
    // @param: sPwd 管理员密码 字符串
    // @param: iRet 错误代码 无符号短整型
    // @ret: 是否成功设置分辨率 布尔型 ture or false
    EXPORT bool WriteDeviceResolution( int Width, int Height, const char sPwd[16], unsigned short *iRet );

    // 视频时长设置接口：
    EXPORT bool WriteVideoSplitValue( int split, const char sPwd[16], unsigned short *iRet );
    EXPORT bool ReadVideoSplitValue( int *split, const char sPwd[16], unsigned short *iRet );
}

#endif
