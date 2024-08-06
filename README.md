
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
- 查询单个技艺制品成本：POST `/costing/one`

> Request 

` application/json`
``` json
{
  "server": "剑胆琴心",
  "formulaName": "[断浪·裤·铸（无双）]",
  "number": 1
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
    "formulaName": "断浪·裤·铸（无双）",
    "type": null,
    "materialId": "5_47516",
    "number": 1,
    "cost": 15392000,
    "costString": "1539金20银",
    "value": 37999400,
    "valueString": "3799金94银",
    "requiredMap": {
      "火墨": {
        "id": "5_31991",
        "uiId": null,
        "sourceId": "31991",
        "iconId": null,
        "name": "火墨",
        "number": 20,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      },
      "沉香木": {
        "id": "5_30855",
        "uiId": null,
        "sourceId": "30855",
        "iconId": null,
        "name": "沉香木",
        "number": 5,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      },
      "珠贝母": {
        "id": "5_30853",
        "uiId": null,
        "sourceId": "30853",
        "iconId": null,
        "name": "珠贝母",
        "number": 5,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      },
      "【印象】锡锭": {
        "id": "5_3319",
        "uiId": null,
        "sourceId": "3319",
        "iconId": null,
        "name": "【印象】锡锭",
        "number": 8,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      },
      "银鳞": {
        "id": "5_30847",
        "uiId": null,
        "sourceId": "30847",
        "iconId": null,
        "name": "银鳞",
        "number": 20,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      },
      "【印象】铜锭": {
        "id": "5_3316",
        "uiId": null,
        "sourceId": "3316",
        "iconId": null,
        "name": "【印象】铜锭",
        "number": 8,
        "price": 0,
        "searchDate": null,
        "formulas": null,
        "desc": null,
        "link": null
      }
    }
  }
}
```