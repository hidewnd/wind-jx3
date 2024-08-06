
## README

剑三相关数据处理API，基于SpringBoot 3.3.1 

## 环境依赖
- openjdk-21 | openjdk-21-graalvm
- mysql 8.0+

## 部署
- 启动redis：
```shell
docker run -p 6379:6379 -v /home/redis/data:/data \
-v /home/redis/redis.conf:/etc/redis/redis.conf \
--name redis redis
```

- 打包：`mvn clean package -DskipTests`
- 启动：`java -jar costing/target/costing.jar`


## 目录结构描述
```text
├── winds                                                       
├── costing                                         # 成本计算服务
│   ├── src           
│   │   ├── main   
│   │   │   │   ├── java
│   │   │   │   ├── resources
│   │   │   │   ├── ├──application.yml             # 成本计算服务配置文件
├── winds-common                                   # 通用包
├── .gitignore
├── pom.xml
└── README.md
```

## 特性

### V0.0.0.1
- 查询单个技艺制品成本：`/costing/one`