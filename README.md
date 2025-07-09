# MixFileCLI
MixFile( https://github.com/InvertGeek/MixFile )的命令行版本 \
与安卓版区别: \
只能通过网页上传下载 \
需要Java11+运行环境,java -jar mixfile-cli-版本.jar 即可启动 \
由于下载文件会频繁拉取图片二进制数据解密创建大量堆内存，建议添加 -XX:+UseZGC 参数，可大幅降低内存占用
```
java -jar -XX:+UseZGC mixfile-cli-版本.jar
``` 

# 下载
https://github.com/InvertGeek/mixfilecli/releases

## Core Module
https://github.com/InvertGeek/mixfile-core
