import numpy as np
from dataclasses import dataclass
from typing import List, Dict, Optional
from collections import defaultdict
from scipy.spatial import KDTree
import random
import unittest
import os
import random
import time
import tempfile
import shutil

from group_info_test import generate_large_dataset


@dataclass
class GroupLine:
    id: int
    group_name: str
    mark: int
    mark_path: str
    x_offset: int
    y_offset: int
    choose: int

class GroupInfoTool:
    """高性能组信息处理工具，支持百万级数据毫秒级操作"""

    def __init__(self):
        self._groups: List[GroupLine] = []
        # 索引结构
        self._id_to_idx: Dict[int, int] = {}
        self._group_to_indices: Dict[str, List[int]] = defaultdict(list)
        self._kd_tree: Optional[KDTree] = None
        self._points: Optional[np.ndarray] = None

        # 写时复制标记
        self._choose_modified = False
        self._mark_modified = False

    def load_from_file(self, file_path: str) -> None:
        """高效加载大文件"""
        self._groups = []
        self._id_to_idx = {}
        self._group_to_indices = defaultdict(list)
        self._points = []

        # 1. 解析文件
        with open(file_path, 'r', buffering=1024*1024) as f:
            for line in f:
                if not line.strip():
                    continue
                try:
                    parts = line.split(maxsplit=6)
                    if len(parts) < 7:
                        continue

                    gid = int(parts[0])       # 第1列：ID
                    group_name = parts[1]     # 第2列：组名 (字符串)
                    mark = int(parts[2])      # 第3列：Mark
                    mark_path = parts[3]      # 第4列：路径 (字符串)
                    x = int(parts[4])         # 第5列：X Offset
                    y = int(parts[5])         # 第6列：Y Offset
                    choose = int(parts[6])    # 第7列：Choose

                    group = GroupLine(
                        id=gid,
                        group_name=group_name,
                        mark=mark,
                        mark_path=mark_path,
                        x_offset=x,
                        y_offset=y,
                        choose=choose
                    )
                    self._groups.append(group)
                except (ValueError, IndexError):
                    continue

        # 2. 构建索引 (O(n))
        for idx, group in enumerate(self._groups):
            self._id_to_idx[group.id] = idx
            self._group_to_indices[group.group_name].append(idx)
            self._points.append([group.x_offset, group.y_offset])

        # 3. 构建空间索引 (KDTree)
        if self._points:
            self._points = np.array(self._points, dtype=np.int32)
            self._kd_tree = KDTree(self._points)

        # 重置修改标记
        self._choose_modified = False
        self._mark_modified = False

    def parse_to_list(self) -> List[GroupLine]:
        """1. 解析为GroupLine列表"""
        return self._groups.copy()

    def count_marked_by_group(self) -> Dict[str, int]:
        """2. 按组名统计标注数量"""
        result = {}
        for group_name, indices in self._group_to_indices.items():
            count = sum(1 for idx in indices if self._groups[idx].mark == 1)
            result[group_name] = count
        return result

    def count_chosen_by_group(self) -> Dict[str, int]:
        """3. 按组名统计选中数量"""
        result = {}
        for group_name, indices in self._group_to_indices.items():
            count = sum(1 for idx in indices if self._groups[idx].choose == 1)
            result[group_name] = count
        return result

    def select_nearest_groups(self, x: int, y: int, percent: float) -> None:
        """4. 选择最近的percent%组"""
        if not self._kd_tree or percent <= 0:
            return

        total = len(self._groups)
        k = max(1, min(total, int(total * percent / 100.0)))

        # KDTree 查询 O(log n)
        _, indices = self._kd_tree.query(np.array([x, y]), k=k)

        # 写时复制
        if not self._choose_modified:
            self._groups = [GroupLine(**vars(g)) for g in self._groups]
            self._choose_modified = True

        # 批量更新
        for idx in np.atleast_1d(indices):
            self._groups[idx].choose = 1

    def select_random_groups(self, percent: float) -> None:
        """5. 随机选择percent%组"""
        if percent <= 0:
            return

        total = len(self._groups)
        target_count = max(1, min(total, int(total * percent / 100.0)))

        # 蓄水池采样/随机选择索引
        all_indices = list(range(total))
        selected_indices = random.sample(all_indices, target_count)

        # 写时复制
        if not self._choose_modified:
            self._groups = [GroupLine(**vars(g)) for g in self._groups]
            self._choose_modified = True

        for idx in selected_indices:
            self._groups[idx].choose = 1

    def update_mark_status(self, group_name: str, mark_value: int) -> None:
        """6. 修改指定组mark状态"""
        if group_name not in self._group_to_indices:
            return

        # 写时复制
        if not self._mark_modified:
            self._groups = [GroupLine(**vars(g)) for g in self._groups]
            self._mark_modified = True

        for idx in self._group_to_indices[group_name]:
            self._groups[idx].mark = mark_value

    def save_to_file(self, file_path: str) -> None:
        """保存当前状态"""
        with open(file_path, 'w', buffering=1024*1024) as f:
            for group in self._groups:
                f.write(
                    f"{group.id} {group.group_name} {group.mark} "
                    f"{group.mark_path} {group.x_offset} {group.y_offset} {group.choose}\n"
                )


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
        generate_large_dataset(cls.large_file, num_lines=2_000_000)

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
        tool.load_from_file(self.large_file)
        load_time = time.time() - start

        groups = tool.parse_to_list()
        self.assertEqual(len(groups), 2000000)
        # self.assertIsInstance(groups, GroupLine)
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