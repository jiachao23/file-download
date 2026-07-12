# 系统设计

## 前言

本文档详细描述了基于 Spring Boot 2.7 + Vue 3 (Ant Design) 的动态报表生成系统的架构设计。

系统采用前后端分离模式，后端通过策略模式支持多格式渲染，通过责任链模式处理数据聚合与校验，前端提供可视化配置与实时预览功能。

## 技术选型 

| 层级 | 技术组件 | 说明 |
| :--- | :--- | :--- |
| 前端 | Vue 3 + TypeScript | 核心框架，保证类型安全 |
| UI 库 | Ant Design Vue 4.x | 企业级 UI 组件库 |
| 构建工具 | Vite | 快速开发与构建 |
| 后端 | Spring Boot 2.7 | 核心业务逻辑容器 |
| 模板引擎 | POI-TL / EasyExcel | Word/Excel 渲染核心 |
| 设计模式 | 策略模式、责任链模式 | 解耦渲染逻辑与数据处理 |
| 校验机制 | Hibernate Validator | 参数合法性校验 |


## 核心设计模式应用 

* 策略模式 (Strategy Pattern): 用于处理不同的报表格式（Word, PPT, Excel, PDF）和不同的数据处理算法。 
* 责任链模式 (Chain of Responsibility): 用于数据源的校验、清洗、转换、聚合流程，支持动态插拔处理节点。 
* 组合模式 (Composite Pattern): 用于报表模版的层级结构（报表由多个区块组成，区块由多个组件/占位符组成）。 
* 工厂模式 (Factory Pattern): 用于创建具体的渲染器或图表组件实例。 
* 观察者模式 (Observer Pattern): 用于异步生成大报表时的状态通知。


## 架构分层图

```plantuml
@startuml
skinparam componentStyle rectangle
skinparam monochrome true

package "Client Layer (Vue 3)" {
  [ReportGenerator View] as View
  [API Service (Axios)] as Api
  [Type Definitions] as Types
}

package "Network" {
  [HTTP / JSON / Blob] as Net
}

package "Server Layer (Spring Boot)" {
  package "Controller Layer" {
    [ReportController] as Ctrl
  }
  package "DTO Layer" {
    [GenerateRequest] as ReqDTO
    [Response DTOs] as ResDTO
  }
  package "Service Layer" {
    [TemplateService] as TplSvc
  }
  package "Core Engine" {
    package "Handler Chain (Data Processing)" {
      [ValidationHandler] as ValH
      [AggregationHandler] as AggH
      [DataHandler Interface] as HandlerInt
    }
    package "Strategy Pattern (Rendering)" {
      [RendererFactory] as RenFac
      [ReportRenderer Interface] as RenInt
      [WordRenderer] as WordR
      [ExcelRenderer] as ExcelR
    }
    package "Domain Model" {
      [ReportTemplate] as Template
      [RenderContext] as Context
    }
  }
}

View --> Api : Calls
Api --> Net : HTTP Request
Net --> Ctrl : Receives
Ctrl --> ReqDTO : Binds & Validates (@Valid)
Ctrl --> TplSvc : Fetches Template
Ctrl --> HandlerInt : Executes Chain
HandlerInt ..> ValH : Implements
HandlerInt ..> AggH : Implements
ValH --> Context : Validates Params
AggH --> Context : Enriches Data
Ctrl --> RenFac : Selects Strategy
RenFac --> RenInt : Gets Renderer
RenInt ..> WordR : Implements
RenInt ..> ExcelR : Implements
WordR --> Template : Reads .docx
WordR --> Context : Merges Data
WordR --> Ctrl : Returns byte[]
Ctrl --> Net : Response (Blob)
Net --> Api : Receives Blob
Api --> View : Triggers Download
@enduml
```
## 核心模块详细设计

### 后端核心类设计

展示了数据处理链（Chain of Responsibility）和渲染策略（Strategy）的结构。

```plantuml
@startuml
skinparam classAttributeIconSize 0
skinparam monochrome true

' DTOs
class GenerateRequest {
  -String templateId
  -Map<String, Object> params
  -String targetFormat
  -Boolean async
  +getTemplateId(): String
  +getParams(): Map
}

class RenderContext {
  -String templateId
  -Map<String, Object> rawParams
  -Map<String, Object> processedData
  -byte[] fileContent
  +getProcessedData(): Map
  +setProcessedData(Map): void
}

' Domain Models
class ReportTemplate {
  -String id
  -String type
  -TemplateContent content
}

class TemplateContent {
  -Meta meta
  -List<Component> components
}

' Handler Chain Pattern
interface DataHandler {
  +handle(Context): void
  +setNext(DataHandler): DataHandler
}

abstract class AbstractHandler {
  -DataHandler next
  +setNext(DataHandler): DataHandler
  +handle(Context): void
  #doHandle(Context): void
}

class ValidationHandler {
  +doHandle(Context): void
}

class AggregationHandler {
  +doHandle(Context): void
}

' Strategy Pattern
interface ReportRenderer {
  +supports(String type): boolean
  +render(Template, Context): byte[]
}

class WordRenderer {
  +supports(String): boolean
  +render(Template, Context): byte[]
}

class ExcelRenderer {
  +supports(String): boolean
  +render(Template, Context): byte[]
}

class RendererFactory {
  -List<ReportRenderer> renderers
  +getRenderer(String): ReportRenderer
}

class HandlerChainFactory {
  -ValidationHandler validationHandler
  -AggregationHandler aggregationHandler
  +buildChain(): DataHandler
}

' Controller
class ReportController {
  -TemplateService templateService
  -HandlerChainFactory chainFactory
  -RendererFactory rendererFactory
  +generateReport(GenerateRequest): ResponseEntity<byte[]>
  +previewReport(GenerateRequest): ResponseEntity<byte[]>
}

' Relationships
GenerateRequest --> ReportController : Request Body
ReportController --> RenderContext : Creates
ReportController --> HandlerChainFactory : Uses
ReportController --> RendererFactory : Uses

AbstractHandler <|-- ValidationHandler
AbstractHandler <|-- AggregationHandler
AbstractHandler ..> DataHandler : Implements
DataHandler --> RenderContext : Processes

RendererFactory ..> ReportRenderer : Aggregates
ReportRenderer <|-- WordRenderer
ReportRenderer <|-- ExcelRenderer
ReportRenderer --> RenderContext : Reads Data
ReportRenderer --> ReportTemplate : Reads Template Def

@enduml
```

#### 模块职责说明

* DTO Layer: GenerateRequest 负责接收前端 JSON，通过 @Valid 注解触发 JSR-303 校验。
* Handler Chain:
** ValidationHandler: 校验参数完整性、业务规则（如日期范围）。
** AggregationHandler: 调用外部服务或数据库，补充模版所需的动态数据（如计算总和、增长率），将结果存入 RenderContext.processedData。
* Strategy Pattern:
** RendererFactory: 根据 template.type 或 request.targetFormat 动态选择渲染器。
** WordRenderer: 使用 POI-TL 加载 .docx 模版，合并数据，输出字节流。
* Controller: 协调上述组件，处理文件流的 HTTP 响应头（Content-Disposition）。


### 前端核心组件设计

组件结构图

```plantuml
@startuml
skinparam componentStyle rectangle

package "src/views" {
  component [ReportGenerator.vue] as MainView {
    [Form Section] as FormSec
    [Preview Section] as PrevSec
  }
}

package "src/api" {
  component [report.ts] as ApiService
}

package "src/types" {
  component [report.ts] as Types
}

package "Ant Design Vue" {
  component [a-form] as AForm
  component [a-table] as ATable
  component [a-card] as ACard
}

MainView --> ApiService : Calls generate/preview
MainView --> Types : Imports Interfaces
MainView ..> AForm : Composes
MainView ..> ATable : Composes
MainView ..> ACard : Composes
ApiService --> Types : Uses for Typing

@enduml

```

### 关键业务流程时序图

#### 报表生成流程 (同步)

此流程描述了从用户点击“导出”到浏览器下载文件的完整过程。

```plantuml
@startuml
actor User
participant "Frontend\n(View)" as FE
participant "API Service\n(Axios)" as Api
participant "ReportController" as Ctrl
participant "HandlerChain" as Chain
participant "RendererFactory" as Factory
participant "WordRenderer" as Renderer
database "TemplateStore" as DB

User -> FE : 点击"生成并下载"
FE -> FE : 表单校验 (Antd Rules)
activate FE
FE -> Api : post('/generate', Request)
activate Api
Api -> Ctrl : HTTP POST
activate Ctrl

Ctrl -> Ctrl : @Valid 校验 DTO
Ctrl -> DB : getTemplate(id)
activate DB
DB --> Ctrl : Return Template Meta
deactivate DB

Ctrl -> Chain : handle(context)
activate Chain
Chain -> Chain : ValidationHandler.check()
Chain -> Chain : AggregationHandler.enrich()
Chain --> Ctrl : Context with Data
deactivate Chain

Ctrl -> Factory : getRenderer(type)
activate Factory
Factory --> Ctrl : Return WordRenderer
deactivate Factory

Ctrl -> Renderer : render(template, context)
activate Renderer
Renderer -> Renderer : Load .docx stream
Renderer -> Renderer : Merge Data (POI-TL)
Renderer --> Ctrl : byte[] fileContent
deactivate Renderer

Ctrl --> Api : ResponseEntity(blob)
deactivate Ctrl
Api --> FE : Blob Response
deactivate Api

FE -> FE : Create Blob URL
FE -> FE : Trigger <a> download
FE --> User : 浏览器开始下载
deactivate FE

@enduml
```

#### 数据预览与校验流程

此流程描述用户点击“预览”时，后端仅进行数据校验和模拟渲染，前端展示模拟表格的过程。

```plantuml
@startuml
actor User
participant "Frontend\n(View)" as FE
participant "Backend\n(Controller)" as Ctrl
participant "HandlerChain" as Chain

User -> FE : 点击"预览数据校验"
FE -> FE : 触发表单校验
FE -> Ctrl : post('/preview', Request)
activate Ctrl

Ctrl -> Ctrl : @Valid 校验 DTO
Ctrl -> Chain : handle(context)
activate Chain
Chain -> Chain : 1. 校验参数合法性
Chain -> Chain : 2. 执行数据聚合逻辑\n(模拟计算 totalSales 等)
Chain --> Ctrl : Context (processedData)
deactivate Chain

note right of Chain
  此处不执行真正的
  文件渲染，仅验证数据
  是否可获取
end note

Ctrl --> FE : 200 OK (Empty/Blob)
deactivate Ctrl

FE -> FE : 更新状态 hasPreviewed = true
FE -> FE : 读取 mockData / Context
FE -> FE : 渲染 Antd Table 组件
FE --> User : 展示数据预览表格
@enduml

```

### 数据模型设计

请求对象 (GenerateRequest)

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `templateId` | String | 是 | 模版唯一标识 |
| `params` | Map | 是 | 动态参数集合 (标题、日期等) |
| `targetFormat` | String | 否 | 覆盖默认格式 (WORD/EXCEL/PDF) |
| `async` | Boolean | 否 | 是否异步处理 (默认 false) |


渲染上下文 (RenderContext)

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `rawParams` | Map | 原始输入参数 |
| `processedData` | Map | 经过责任链处理后的完整数据 (含聚合数据) |
| `fileContent` | byte[] | 最终生成的文件二进制流 |



### 模版管理子系统 (Template Management)

模版数据结构设计 为了支持在线编辑和多种格式，模版不直接存储二进制文件，而是存储为 JSON 结构化描述 (DSL)。

模版元数据表

```json
{
  "meta": { "format": "DOCX", "author": "system" },
  "layout": [
    {
      "id": "section_1",
      "type": "HEADER",
      "components": [
        { "type": "TEXT", "placeholder": "${report.title}", "style": {...} },
        { "type": "IMAGE", "placeholder": "${company.logo}", "width": 100 }
      ]
    },
    {
      "id": "section_2",
      "type": "CHART_CONTAINER",
      "chartConfig": { "templateId": "bar_chart_v1", "dataSourceRef": "ds_sales_01" },
      "placeholder": "${chart.sales_bar}"
    }
  ]
}
```

### 在线编辑器

* 技术选型: 使用 Monaco Editor 编辑 JSON，或基于 Konva.js / Fabric.js 实现拖拽式可视化编辑。
* 占位符定义: 提供UI界面让用户插入 ${variable}，支持类型选择（文本、图片、循环列表、表达式）。
* 内置模版库: 预置一组标准 Chart Config JSON（如柱状图、饼图配置），用户可拖入画布并绑定数据源。

### 数据处理引擎

责任链设计

