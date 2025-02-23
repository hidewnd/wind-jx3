
## README

剑三相关数据处理API，基于SpringBoot 3.3.1

## 环境依赖
- openjdk-21 | openjdk-21-graalvm


## 部署
- 启动redis：
```shell

```

- 打包：`mvn clean package -DskipTests`
- 启动：`java -jar costing/target/winds-costing.jar`


- Docker部署

> jdk21运行镜像构建文件

```dockerfile
FROM meltwaterfoundation/drone-maven-jdk21:latest
WORKDIR /app
EXPOSE 9001
CMD ["sh", "-c", "java -jar --enable-preview /app/${JAR_NAME}"]
```

> 部署服务

```shell
# 部署redis
docker run -d -p 6379:6379 \
-v /home/redis/data:/data \
-v /home/redis/redis.conf:/etc/redis/redis.conf \
--name redis redis

#构建jdk21运行环境
docker build -t jdk21env:0.0.1 .
# jdk21运行镜像编译
docker build -f dockerfile -t hidewnd/winds-costing:0.0.2 .
# 成本预算服务部署
docker run -d -p 9001:9001\
-v /home/winds/:/app \
-e JAR_NAME=winds-costing.jar \
--name winds_costing jdk21env:0.0.1

```


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

> 字段说明

- `server`：服务器名称
- `formulaName`：技艺制品名称
- `number`：数量
- `cost`：成本价格
- `rangeCreate`: 是否随机生成，否则按配方最小生成数计算
- `costString`：成本价格格式化
- `value`：交易行价格
- `valueString`：交易行价格格式化
- `actualProfit`：实际利润
- `actualNumber`: 实际产出数量
- `actualProfitString`：实际利润格式化
- `requiredMap`：所需材料数量

### V0.0.0.2
- 查询单个技艺制品成本：POST `/costing/one`

> Request 

` application/json`
``` json
{
  "server": "剑胆琴心",
  "formulaName": "[龙血磨石]",
  "number": 1,
  "rangeCreate": true
}

```

> Response

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "server": null,
    "type": null,
    "formulaName": "龙血磨石",
    "materialId": "5_31139",
    "number": 1,
    "cost": 13682720,
    "costString": "1368金27银20铜",
    "energies": 120,
    "value": 15003900,
    "valueString": "1500金39银",
    "actualNumber": 1,
    "actualProfit": 570985,
    "actualProfitString": "57金9银85铜",
    "requiredMap": {
      "沉香木": {
        "id": "5_30855",
        "name": "沉香木",
        "number": 5,
        "value": 7074000,
        "valueString": "707金40银"
      },
      "熟铁锭": {
        "id": "5_71115",
        "name": "熟铁锭",
        "number": 20,
        "value": 3803980,
        "valueString": "380金39银80铜"
      },
      "硼砂": {
        "id": "5_30679",
        "name": "硼砂",
        "number": 20,
        "value": 825740,
        "valueString": "82金57银40铜"
      },
      "木炭": {
        "id": "5_31992",
        "name": "木炭",
        "number": 20,
        "value": 120000,
        "valueString": "12金"
      },
      "猫眼石": {
        "id": "5_30852",
        "name": "猫眼石",
        "number": 5,
        "value": 1859000,
        "valueString": "185金90银"
      }
    },
    "makeDetail": [
      {
        "no": 0,
        "name": "龙血磨石",
        "makeNumber": 1
      }
    ]
  }
}

```


- 代理请求JX3API：POST `/proxy/jx3api`

> Request

```json
{
  "url": "/data/saohua/random",
  "params": {}
}
```
> Response

```json
{
  "success": true,
  "code": 2000,
  "msg": "代理请求成功",
  "obj": {
    "code": 200,
    "msg": "success",
    "data": {
      "id": 2655,
      "text": "情缘请主动加我好友"
    },
    "time": 1723388083
  }
}
```