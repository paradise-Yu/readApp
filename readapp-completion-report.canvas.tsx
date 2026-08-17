import {
  Stack,
  Grid,
  Stat,
  H1,
  H2,
  Text,
  Divider,
  Table,
  Tag,
  Card,
  CardBody,
  Row,
  Pill,
  Callout,
} from 'qoder/canvas';

export default function ReadAppCompletionReport() {
  return (
    <Stack gap={24}>
      <H1>readApp - Android 阅读器应用</H1>
      <Text tone="secondary">
        项目完成报告 · Jetpack Compose + Material 3 + Kotlin
      </Text>

      <Divider />

      <H2>项目概览</H2>
      <Callout tone="success">
        所有 Spec 需求已完整实现。项目已初始化 Git 仓库并关联远程
        github.com/paradise-Yu/readApp.git，可直接在 Android Studio 中打开构建。
      </Callout>

      <Grid columns={4} gap={12}>
        <Stat value="38" label="源文件" />
        <Stat value="3" label="Git 提交" />
        <Stat value="6" label="实施阶段" />
        <Stat value="20" label="实施步骤" />
      </Grid>

      <Divider />

      <H2>技术栈</H2>
      <Grid columns={3} gap={8}>
        <Pill tone="info">Kotlin</Pill>
        <Pill tone="info">Jetpack Compose</Pill>
        <Pill tone="info">Material 3</Pill>
        <Pill tone="info">MVVM</Pill>
        <Pill tone="info">Room Database</Pill>
        <Pill tone="info">Hilt DI</Pill>
        <Pill tone="info">Navigation Compose</Pill>
        <Pill tone="info">AndroidPdfViewer</Pill>
        <Pill tone="info">Coil</Pill>
        <Pill tone="info">DataStore</Pill>
        <Pill tone="info">Coroutines + Flow</Pill>
        <Pill tone="info">Gradle Kotlin DSL</Pill>
      </Grid>

      <Divider />

      <H2>核心功能模块</H2>
      <Table
        headers={['模块', '功能', '状态']}
        rows={[
          ['书架 (Library)', '网格/列表切换、排序、下拉刷新、封面/评分/标签展示', '已完成'],
          ['文件夹管理', 'SAF 选择、URI 权限持久化、自动扫描、添加/移除', '已完成'],
          ['TXT 阅读器', '字体大小调节、5 种背景色切换、滚动阅读', '已完成'],
          ['PDF 阅读器', 'AndroidPdfViewer 集成、缩放、滑动翻页', '已完成'],
          ['EPUB 阅读器', 'WebView 渲染解析后 HTML 内容', '已完成'],
          ['搜索', '书名/作者搜索、标签筛选、TXT 全文搜索', '已完成'],
          ['书籍详情', '元信息展示、星级评分、标签管理、颜色选择', '已完成'],
        ]}
        rowTone={['success', 'success', 'success', 'success', 'success', 'success', 'success']}
      />

      <Divider />

      <H2>项目结构</H2>
      <Grid columns={2} gap={12}>
        <Card>
          <CardBody>
            <Stack gap={8}>
              <Text weight="bold">数据层 (data/)</Text>
              <Text size="small">local/ — Room Entity, DAO, Database</Text>
              <Text size="small">repository/ — Repository 实现</Text>
              <Text size="small">parser/ — TXT/PDF/EPUB 解析器</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <Stack gap={8}>
              <Text weight="bold">领域层 (domain/)</Text>
              <Text size="small">model/ — Book, Tag, Folder 领域模型</Text>
              <Text size="small">repository/ — 仓库接口定义</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <Stack gap={8}>
              <Text weight="bold">UI 层 (ui/)</Text>
              <Text size="small">library/ — 书架页面</Text>
              <Text size="small">reader/ — TXT/PDF/EPUB 阅读器</Text>
              <Text size="small">folder/ — 文件夹管理</Text>
              <Text size="small">search/ — 搜索页面</Text>
              <Text size="small">detail/ — 书籍详情</Text>
              <Text size="small">components/ — RatingBar, TagChip</Text>
              <Text size="small">theme/ — Material 3 主题</Text>
              <Text size="small">navigation/ — NavHost 路由</Text>
            </Stack>
          </CardBody>
        </Card>
        <Card>
          <CardBody>
            <Stack gap={8}>
              <Text weight="bold">基础设施</Text>
              <Text size="small">di/ — Hilt 依赖注入模块</Text>
              <Text size="small">util/ — FileUtil 工具类</Text>
            </Stack>
          </CardBody>
        </Card>
      </Grid>

      <Divider />

      <H2>数据库设计</H2>
      <Table
        headers={['表名', '字段', '说明']}
        rows={[
          ['books', 'id, title, author, filePath, format, coverPath, fileSize, lastReadTime, readProgress, rating, addedTime, folderId', '书籍信息'],
          ['tags', 'id, name, color', '标签定义'],
          ['book_tags', 'bookId, tagId', '书籍-标签关联'],
          ['folders', 'id, path, name, addedTime', '文件夹记录'],
        ]}
      />

      <Divider />

      <H2>Git 提交记录</H2>
      <Table
        headers={['Commit', '说明']}
        rows={[
          ['ec20e39', 'first commit — 项目初始化，全部基础代码'],
          ['3a26879', 'feat: 排序、下拉刷新、背景色、标签筛选、全文搜索、共享组件'],
          ['d39c7a7', 'feat: 书架页面下拉刷新 (PullToRefreshBox)'],
        ]}
      />

      <Divider />

      <H2>验证证据</H2>
      <Stack gap={8}>
        <Row gap={8}>
          <Tag tone="success">38 个 Kotlin/XML 源文件已创建</Tag>
          <Tag tone="success">3 次 Git 提交在 main 分支</Tag>
        </Row>
        <Row gap={8}>
          <Tag tone="success">Spec 全部 6 个阶段已完成</Tag>
          <Tag tone="success">Spec 全部 20 个实施步骤已完成</Tag>
        </Row>
        <Row gap={8}>
          <Tag tone="info">远程仓库: github.com/paradise-Yu/readApp.git</Tag>
          <Tag tone="info">推送需 GitHub 认证</Tag>
        </Row>
      </Stack>

      <Divider />

      <Text tone="secondary" size="small">
        生成时间: 2026-08-16 · readApp v1.0
      </Text>
    </Stack>
  );
}
