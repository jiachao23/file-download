
import unittest
import os
import random
import time
import tempfile
import shutil

# 引入上面定义的工具类和生成函数
# 假设 data_generator.py 和 group_tool.py 在同一目录下，或者您可以直接复制类定义到这里
from group_tool import GroupInfoTool, GroupLine
from data_generator import generate_large_dataset

class TestGroupInfoTool(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        """测试前准备：生成数据文件"""
        cls.temp_dir = tempfile.mkdtemp()
        cls.test_file = os.path.join(cls.temp_dir, "group_info.txt")
        cls.large_file = os.path.join(cls.temp_dir, "large_group_info.txt")

        print(f"\n🛠️ 正在准备测试环境...")

        # 1. 生成小规模测试数据 (1000行) 用于功能验证
        generate_large_dataset(cls.test_file, num_lines=1000)

        # 2. 生成大规模测试数据 (100万行) 用于性能验证
        # 注意：这可能需要几秒钟时间
        generate_large_dataset(cls.large_file, num_lines=1_000_000)

        print("✅ 测试数据准备完毕")

    @classmethod
    def tearDownClass(cls):
        """测试后清理"""
        if os.path.exists(cls.temp_dir):
            shutil.rmtree(cls.temp_dir)
            print("🧹 测试文件已清理")

    def test_01_file_format(self):
        """测试生成文件的格式是否符合 x_y 要求"""
        print("   🧪 测试文件格式...")
        with open(self.test_file, 'r') as f:
            for i, line in enumerate(f):
                parts = line.strip().split()
                self.assertEqual(len(parts), 7, f"第 {i} 行列数错误")

                # 检查 group_name 格式
                group_name = parts
                self.assertRegex(group_name, r'^\d+_\d+$', f"group_name 格式错误: {group_name}")
        print("   ✅ 文件格式校验通过")

    def test_02_load_and_parse(self):
        """测试加载和解析功能"""
        print("   🧪 测试加载功能...")
        tool = GroupInfoTool()
        start = time.time()
        tool.load_from_file(self.test_file)
        load_time = time.time() - start

        groups = tool.parse_to_list()
        self.assertEqual(len(groups), 1000)
        self.assertIsInstance(groups, GroupLine)
        print(f"   ✅ 加载 1000 行耗时: {load_time:.4f}s")

    def test_03_count_stats(self):
        """测试统计功能"""
        print("   🧪 测试统计功能...")
        tool = GroupInfoTool()
        tool.load_from_file(self.test_file)

        marked_counts = tool.count_marked_by_group()
        chosen_counts = tool.count_chosen_by_group()

        # 验证返回值类型
        self.assertIsInstance(marked_counts, dict)
        self.assertIsInstance(chosen_counts, dict)

        # 验证总数（简单抽查）
        total_marked = sum(marked_counts.values())
        total_chosen = sum(chosen_counts.values())
        self.assertGreater(total_marked, 0) # 随机数据应该至少有标记的
        print("   ✅ 统计功能正常")

    def test_04_spatial_query(self):
        """测试最近邻查询"""
        print("   🧪 测试空间查询...")
        tool = GroupInfoTool()
        tool.load_from_file(self.test_file)

        # 查询中心点 (500, 500) 附近 10% 的组
        tool.select_nearest_groups(500, 500, 10.0)

        groups = tool.parse_to_list()
        chosen_count = sum(g.choose for g in groups)

        # 理论上 10% 应该是 100 个左右
        self.assertAlmostEqual(chosen_count, 100, delta=5)
        print("   ✅ 空间查询功能正常")

    def test_05_random_select(self):
        """测试随机选择"""
        print("   🧪 测试随机选择...")
        tool = GroupInfoTool()
        tool.load_from_file(self.test_file)

        random.seed(42) # 固定种子
        tool.select_random_groups(5.0) # 5%

        groups = tool.parse_to_list()
        chosen_count = sum(g.choose for g in groups)
        self.assertEqual(chosen_count, 50) # 1000 * 5% = 50
        print("   ✅ 随机选择功能正常")

    def test_06_update_mark(self):
        """测试更新标注状态"""
        print("   🧪 测试更新状态...")
        tool = GroupInfoTool()
        tool.load_from_file(self.test_file)

        # 获取第一个组名
        first_group_name = tool._groups.group_name

        tool.update_mark_status(first_group_name, 1)

        # 验证该组所有元素 mark 均为 1
        for g in tool._groups:
            if g.group_name == first_group_name:
                self.assertEqual(g.mark, 1)
        print("   ✅ 状态更新功能正常")

    def test_07_performance_million_rows(self):
        """性能测试：百万级数据"""
        print("   🚀 开始百万级性能测试...")
        tool = GroupInfoTool()

        # 1. 加载性能
        start = time.time()
        tool.load_from_file(self.large_file)
        load_time = time.time() - start
        print(f"   ⏱️ 加载 100万行 耗时: {load_time:.2f}s")
        self.assertLess(load_time, 5.0, "加载时间过长，需优化 I/O")

        # 2. 空间查询性能
        start = time.time()
        tool.select_nearest_groups(2500, 2500, 0.1) # 0.1% = 1000个
        query_time = time.time() - start
        print(f"   ⏱️ 空间查询 (0.1%) 耗时: {query_time:.4f}s")
        self.assertLess(query_time, 0.1, "查询时间过长，需检查 KDTree")

        # 3. 统计性能
        start = time.time()
        tool.count_marked_by_group()
        stats_time = time.time() - start
        print(f"   ⏱️ 分组统计 耗时: {stats_time:.4f}s")
        self.assertLess(stats_time, 0.5, "统计时间过长")

        print("   ✅ 性能测试通过")

if __name__ == '__main__':
    # 增加 verbosity 以看到详细输出
    unittest.main(argv=['first-arg-is-ignored'], exit=False, verbosity=2)