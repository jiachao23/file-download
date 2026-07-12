
import os
import mmap
import threading
import tempfile
import shutil
import time
import random
import unittest
from collections import OrderedDict
from typing import List, Optional, Dict, Any


class SampleLine:
    """支持动态扩展字段的数据集单行对象"""
    BASE_FIELDS = ['id', 'type', 'status', 'rel_x', 'rel_y', 'size_x', 'size_y', 'mark', 'path']
    __slots__ = ('base_data', 'extra_fields')

    def __init__(self, base_data: dict, extra_fields: dict = None):
        self.base_data = base_data
        self.extra_fields = extra_fields or {}

    def __getattr__(self, name):
        """支持 obj.rel_x 或 obj.new_field 的访问方式"""
        if name in self.base_data:
            return self.base_data[name]
        if name in self.extra_fields:
            return self.extra_fields[name]
        raise AttributeError(f"'SampleLine' object has no attribute '{name}'")

    def __repr__(self):
        return f"SampleLine(id={self.base_data.get('id')}, type={self.base_data.get('type')})"

    @staticmethod
    def parse(line_bytes: bytes) -> 'SampleLine':
        """动态解析：前8个为基础字段，剩余部分全部归为扩展字段"""
        parts = line_bytes.split(b' ', 8)
        base_map = {}
        try:
            base_map['id'] = int(parts[0])
            base_map['type'] = int(parts[1])
            base_map['status'] = int(parts[2])
            base_map['rel_x'] = float(parts[3])
            base_map['rel_y'] = float(parts[4])
            base_map['size_x'] = float(parts[5])
            base_map['size_y'] = float(parts[6])
            base_map['mark'] = parts[7].decode('utf-8')
        except Exception as e:
            raise ValueError(f"基础字段解析失败: {e}")

        extra_map = {}
        if len(parts) > 8:
            remaining = parts[8].decode('utf-8').rstrip('\n\r')
            remaining_parts = remaining.split(' ')
            if remaining_parts:
                base_map['path'] = remaining_parts[0]
                for item in remaining_parts[1:]:
                    if '=' in item:
                        k, v = item.split('=', 1)
                        try:
                            extra_map[k] = float(v) if '.' in v else int(v)
                        except:
                            extra_map[k] = v
            else:
                base_map['path'] = ""
        else:
            base_map['path'] = parts[8].decode('utf-8').rstrip('\n\r')

        return SampleLine(base_map, extra_map)

    def to_bytes(self) -> bytes:
        """序列化回字节流"""
        base_vals = [str(self.base_data[f]) for f in self.BASE_FIELDS]
        extra_str = " ".join([f"{k}={v}" for k, v in self.extra_fields.items()])
        if extra_str:
            line = " ".join(base_vals + [extra_str]) + "\n"
        else:
            line = " ".join(base_vals) + "\n"
        return line.encode('utf-8')


class LRUUpdateCache:
    """通用 LRU 缓存，支持缓存任意字段的更新"""
    def __init__(self, capacity: int = 10000):
        self.capacity = capacity
        self.cache: OrderedDict[int, Dict[str, Any]] = OrderedDict()
        self.lock = threading.Lock()

    def get(self, sample_id: int) -> Optional[Dict[str, Any]]:
        with self.lock:
            if sample_id in self.cache:
                self.cache.move_to_end(sample_id)
                return self.cache[sample_id].copy()
            return None

    def put(self, sample_id: int, updates: Dict[str, Any]):
        with self.lock:
            if sample_id in self.cache:
                self.cache.move_to_end(sample_id)
                self.cache[sample_id].update(updates)
            else:
                if len(self.cache) >= self.capacity:
                    self.cache.popitem(last=False)
                self.cache[sample_id] = updates.copy()

    def flush_all(self) -> Dict[int, Dict[str, Any]]:
        with self.lock:
            data = dict(self.cache)
            self.cache.clear()
            return data


class DatasetManager:
    """高性能数据集管理器，支持变长字段更新与索引重建"""

    def __init__(self, file_path: str, cache_capacity: int = 10000):
        self.file_path = file_path
        self.cache = LRUUpdateCache(capacity=cache_capacity)
        self._mm: Optional[mmap.mmap] = None
        self._file_handle = None
        self._line_offsets: List[int] = []
        self._total_lines = 0
        self._lock = threading.Lock()
        self._load_index()

    def _load_index(self):
        """毫秒级解析：仅构建行偏移量索引"""
        if self._mm:
            self._mm.close()
        if self._file_handle:
            self._file_handle.close()

        if not os.path.exists(self.file_path):
            with open(self.file_path, 'wb') as f: pass

        self._file_handle = open(self.file_path, 'r+b')
        file_size = os.path.getsize(self.file_path)

        if file_size == 0:
            self._mm = mmap.mmap(self._file_handle.fileno(), 0)
            self._line_offsets = []
            self._total_lines = 0
            return

        self._mm = mmap.mmap(self._file_handle.fileno(), 0, access=mmap.ACCESS_WRITE)

        offsets = []
        pos = 0
        while True:
            next_pos = self._mm.find(b'\n', pos)
            if next_pos == -1:
                if pos < file_size: offsets.append(pos)
                break
            offsets.append(pos)
            pos = next_pos + 1

        self._line_offsets = offsets
        self._total_lines = len(offsets)

    def _get_raw_line(self, line_idx: int) -> bytes:
        if line_idx < 0 or line_idx >= self._total_lines:
            raise IndexError("Line index out of range")
        start = self._line_offsets[line_idx]
        end = self._line_offsets[line_idx + 1] if line_idx + 1 < self._total_lines else len(self._mm)
        return self._mm[start:end]

    def get_line(self, line_idx: int) -> SampleLine:
        """读取指定行，毫秒级"""
        raw_bytes = self._get_raw_line(line_idx)
        first_space = raw_bytes.find(b' ')
        if first_space == -1: raise ValueError("Invalid line")
        sid = int(raw_bytes[:first_space])

        updates = self.cache.get(sid)
        if updates:
            line = SampleLine.parse(raw_bytes)
            for k, v in updates.items():
                if k in line.base_data: line.base_data[k] = v
                else: line.extra_fields[k] = v
            return line
        return SampleLine.parse(raw_bytes)

    def update_fields(self, sample_id: int, **kwargs):
        """通用更新接口，写入 LRU 缓存"""
        self.cache.put(sample_id, kwargs)

    def sync_to_disk(self):
        """
        变长更新的核心：刷盘逻辑。
        策略：读取原文件，在内存中应用缓存更新，写入临时文件，最后替换原文件并重建索引。
        """
        dirty_data = self.cache.flush_all()
        if not dirty_data:
            return

        with self._lock:
            dir_name = os.path.dirname(self.file_path) or '.'
            fd, temp_path = tempfile.mkstemp(dir=dir_name)
            os.close(fd)

            try:
                with open(temp_path, 'wb') as temp_file:
                    for i in range(self._total_lines):
                        raw_bytes = self._get_raw_line(i)
                        first_space = raw_bytes.find(b' ')
                        if first_space == -1: continue
                        sid = int(raw_bytes[:first_space])

                        if sid in dirty_data:
                            updates = dirty_data[sid]
                            line = SampleLine.parse(raw_bytes)
                            for k, v in updates.items():
                                if k in line.base_data: line.base_data[k] = v
                                else: line.extra_fields[k] = v
                            temp_file.write(line.to_bytes())
                        else:
                            temp_file.write(raw_bytes)

                self._mm.close()
                self._file_handle.close()
                shutil.move(temp_path, self.file_path)
                self._load_index()

            except Exception as e:
                if os.path.exists(temp_path):
                    os.remove(temp_path)
                raise e

    def filter_and_paginate(self, page: int = 1, page_size: int = 20, **filters) -> Dict:
        """结合过滤与分页。支持 range_min/max 和精确匹配"""
        results = []
        matched_count = 0
        start_idx = (page - 1) * page_size
        end_idx = start_idx + page_size

        for i in range(self._total_lines):
            raw = self._get_raw_line(i)
            first_space = raw.find(b' ')
            if first_space == -1: continue
            sid = int(raw[:first_space])

            updates = self.cache.get(sid)
            if updates:
                line = SampleLine.parse(raw)
                for k, v in updates.items():
                    if k in line.base_data: line.base_data[k] = v
                    else: line.extra_fields[k] = v
            else:
                line = SampleLine.parse(raw)

            valid = True
            for key, condition in filters.items():
                if key.endswith('_min'):
                    field_name = key[:-4]
                    val = getattr(line, field_name, None)
                    if val is None or val < condition: valid = False; break
                elif key.endswith('_max'):
                    field_name = key[:-4]
                    val = getattr(line, field_name, None)
                    if val is None or val > condition: valid = False; break
                else:
                    val = getattr(line, key, None)
                    if val != condition: valid = False; break

            if not valid: continue

            matched_count += 1
            if start_idx <= matched_count - 1 < end_idx:
                results.append(line)

        return {"page": page, "page_size": page_size, "total": matched_count, "data": results}

    def close(self):
        self.sync_to_disk()
        if self._mm: self._mm.close()
        if self._file_handle: self._file_handle.close()

    def __enter__(self): return self
    def __exit__(self, exc_type, exc_val, exc_tb): self.close()





class TestDatasetManager(unittest.TestCase):
    TEST_FILE = "test_sample_final.txt"
    TOTAL_LINES = 100000

    @classmethod
    def setUpClass(cls):
        print(f"\n正在生成 {cls.TOTAL_LINES} 行测试数据...")
        with open(cls.TEST_FILE, 'w') as f:
            for i in range(cls.TOTAL_LINES):
                line = f"{i} {random.randint(1, 5)} {random.randint(0, 1)} " \
                       f"{random.uniform(0, 100):.4f} {random.uniform(0, 100):.4f} " \
                       f"{random.uniform(10, 50):.4f} {random.uniform(10, 50):.4f} " \
                       f"mark_{i % 10} /path/to/sample_{i}.jpg\n"
                f.write(line)
        print("测试数据生成完毕。")

    @classmethod
    def tearDownClass(cls):
        if os.path.exists(cls.TEST_FILE):
            os.remove(cls.TEST_FILE)

    def test_1_init_performance(self):
        """测试解析索引耗时"""
        start = time.time()
        with DatasetManager(self.TEST_FILE) as dm:
            elapsed = (time.time() - start) * 1000
            print(f"[性能测试] 解析 {self.TOTAL_LINES} 行索引耗时: {elapsed:.2f} ms")
            self.assertEqual(dm._total_lines, self.TOTAL_LINES)

    def test_2_read_performance(self):
        """测试随机读取耗时"""
        with DatasetManager(self.TEST_FILE) as dm:
            start = time.time()
            for _ in range(1000):
                idx = random.randint(0, self.TOTAL_LINES - 1)
                dm.get_line(idx)
            elapsed = (time.time() - start) * 1000
            print(f"[性能测试] 随机读取 1000 行平均耗时: {(elapsed/1000):.4f} ms/行")

    def test_3_update_and_consistency(self):
        """测试变长更新与一致性"""
        with DatasetManager(self.TEST_FILE, cache_capacity=5) as dm:
            target_id = 5000
            # 更新 type 并新增一个变长字段
            dm.update_fields(target_id, type=99, color='red', score=100)

            # 内存中验证
            line = dm.get_line(5000)
            self.assertEqual(line.type, 99)
            self.assertEqual(line.color, 'red')

            # 刷盘并验证磁盘数据
            dm.sync_to_disk()
            with DatasetManager(self.TEST_FILE) as dm2:
                line2 = dm2.get_line(5000)
                self.assertEqual(line2.type, 99)
                self.assertEqual(line2.extra_fields['color'], 'red')
                print("[一致性测试] 变长更新刷盘后数据验证通过。")

    def test_4_filter_and_paginate(self):
        """测试过滤与分页"""
        with DatasetManager(self.TEST_FILE) as dm:
            result = dm.filter_and_paginate(
                rel_x_min=50.0, rel_x_max=60.0, page=2, page_size=10
            )
            self.assertEqual(result['page'], 2)
            for item in result['data']:
                self.assertGreaterEqual(item.rel_x, 50.0)
            print(f"[过滤分页测试] 匹配总数: {result['total']}")

    def test_5_lru_cache_eviction(self):
        """测试 LRU 缓存淘汰"""
        with DatasetManager(self.TEST_FILE, cache_capacity=3) as dm:
            dm.update_fields(1, type=10)
            dm.update_fields(2, type=20)
            dm.update_fields(3, type=30)
            dm.update_fields(4, type=40)
            dirty = dm.cache.flush_all()
            self.assertNotIn(1, dirty)
            self.assertIn(4, dirty)
            print("[LRU测试] 缓存淘汰机制验证通过。")

if __name__ == '__main__':
    unittest.main(verbosity=2)
