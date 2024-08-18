
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
- 启动：`java -jar costing/target/costing.jar`
- Docker部署

Dockerfile
```dockerfile
FROM openjdk:21
WORKDIR /app
COPY costing.jar /app/costing.jar
ENTRYPOINT ["java","--enable-preview","-jar","/app/costing.jar"]
```
运行
```shell
# 镜像编译
docker build -f dockerfile -t hidewnd/costing:0.0.1 .
# 容器运行
docker run -d --net=bridge -p 9001:9001 \
-v /home/costing.jar:/app/costing.jar \
hidewnd/costing:0.0.1
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
- `costString`：成本价格格式化
- `value`：交易行价格
- `valueString`：交易行价格格式化
- `actualProfit`：实际利润
- `actualProfitString`：实际利润格式化
- `requiredMap`：所需材料数量

### V0.0.0.1
- 查询单个技艺制品成本：POST `/costing/one`

> Request 

` application/json`
``` json
{
  "server": "剑胆琴心",
  "formulaName": "[断浪·腕·绣（外破）]",
  "number": 2
}
```

> Response

```json
{
  "success": true,
  "code": 2000,
  "msg": "请求成功",
  "obj": {
    "materialId": "5_47568",
    "server": "剑胆琴心",
    "type": null,
    "formulaName": "断浪·腕·绣（外破）",
    "number": 2,
    "cost": 29037600,
    "costString": "2903金76银",
    "value": 43995600,
    "valueString": "4399金56银",
    "actualProfit": 12758220,
    "actualProfitString": "1275金82银20铜",
    "requiredMap": {
      "玛瑙": {
        "name": "玛瑙",
        "number": 16
      },
      "百股线": {
        "name": "百股线",
        "number": 8
      },
      "沉香木": {
        "name": "沉香木",
        "number": 10
      },
      "百染线": {
        "name": "百染线",
        "number": 80
      },
      "银鳞": {
        "name": "银鳞",
        "number": 20
      },
      "棉线": {
        "name": "棉线",
        "number": 120
      },
      "猫眼石": {
        "name": "猫眼石",
        "number": 10
      },
      "补阙石": {
        "name": "补阙石",
        "number": 40
      }
    }
  }
}
```


- 查询多个技艺制品成本：POST `/costing/list`

> Request

```json
{
  "server": "剑胆琴心",
  "items": [
    {
      "formulaName": "断浪·上品破秽散",
      "number": 10
    },
    {
      "formulaName": "断浪·太后饼",
      "number": 10
    }
  ]
}

```

> Response

```json
{
  "success": true,
  "code": 2000,
  "msg": "请求成功",
  "obj": {
    "server": "剑胆琴心",
    "items": [
      {
        "materialId": "5_47613",
        "server": "剑胆琴心",
        "formulaName": "断浪·上品破秽散",
        "number": 10
      },
      {
        "materialId": "5_47642",
        "server": "剑胆琴心",
        "formulaName": "断浪·太后饼",
        "number": 10
      }
    ],
    "cost": 6479700,
    "costString": "647金97银",
    "value": 23261000,
    "valueString": "2326金10银",
    "actualProfit": 16664995,
    "actualProfitString": "1666金49银95铜",
    "requiredMap": {
      "杂碎": {
        "name": "杂碎",
        "number": 35
      },
      "血": {
        "name": "血",
        "number": 35
      },
      "五味子": {
        "name": "五味子",
        "number": 40
      },
      "精制面粉": {
        "name": "精制面粉",
        "number": 50
      },
      "金针": {
        "name": "金针",
        "number": 80
      },
      "调料": {
        "name": "调料",
        "number": 140
      },
      "蛋": {
        "name": "蛋",
        "number": 50
      },
      "药囊": {
        "name": "药囊",
        "number": 60
      },
      "补阙石": {
        "name": "补阙石",
        "number": 72
      },
      "虫草": {
        "name": "虫草",
        "number": 40
      },
      "露水": {
        "name": "露水",
        "number": 20
      },
      "药罐": {
        "name": "药罐",
        "number": 60
      },
      "碎肉": {
        "name": "碎肉",
        "number": 75
      },
      "蜂王浆": {
        "name": "蜂王浆",
        "number": 9
      },
      "银鳞": {
        "name": "银鳞",
        "number": 12
      }
    }
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