
## README

剑三相关数据处理API，基于SpringBoot 3.3.1

## 环境依赖
- openjdk-21 | openjdk-21-graalvm


## 部署
- 启动redis：
```shell
docker run -p 6379:6379 -v /home/redis/data:/data \
-v /home/redis/redis.conf:/etc/redis/redis.conf \
--name redis redis
```

- 打包：`mvn clean package -DskipTests`
- 启动：`java -jar costing/target/winds-costing.jar`
- Docker部署

Dockerfile
```dockerfile
FROM openjdk:21
WORKDIR /app
COPY winds-costing.jar /app/costing.jar
ENTRYPOINT ["java","--enable-preview","-jar","/app/costing.jar"]
```
运行
```shell
# 镜像编译
docker build -f dockerfile -t hidewnd/winds-costing:0.0.2 .
# 容器运行
docker run -d --net=bridge -p 9001:9001 \
-v /home/winds-costing.jar:/app/costing.jar \
hidewnd/costing:0.0.2
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
    "cost": 12885406,
    "costString": "1288金54银6铜",
    "energies": 300,
    "value": 15003900,
    "valueString": "1500金39银",
    "actualNumber": 21,
    "actualProfit": 1368299,
    "actualProfitString": "136金82银99铜",
    "requiredMap": {
      "沉香木": {
        "id": "5_30855",
        "name": "沉香木",
        "number": 5,
        "value": 7074000,
        "valueString": "707金40银"
      },
      "血琥珀": {
        "id": "5_71329",
        "name": "血琥珀",
        "number": 45,
        "value": 850500,
        "valueString": "85金5银"
      },
      "硼砂": {
        "id": "5_30679",
        "name": "硼砂",
        "number": 38,
        "value": 1568906,
        "valueString": "156金89银6铜"
      },
      "木炭": {
        "id": "5_31992",
        "name": "木炭",
        "number": 200,
        "value": 1200000,
        "valueString": "120金"
      },
      "铁矿": {
        "id": "5_30677",
        "name": "铁矿",
        "number": 45,
        "value": 333000,
        "valueString": "33金30银"
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
      },
      {
        "no": 0,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 1,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 2,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 3,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 4,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 5,
        "name": "熟铁锭",
        "makeNumber": 3
      },
      {
        "no": 6,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 7,
        "name": "熟铁锭",
        "makeNumber": 2
      },
      {
        "no": 8,
        "name": "熟铁锭",
        "makeNumber": 3
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