import os
import random

def generate_large_dataset(file_path, num_lines=1_000_000):
    """
    生成百万行测试数据
    格式: <id> <group_name> <mark> <mark_path> <x_offset> <y_offset> <choose>
    group_name: x_y (x, y 为整数)
    """
    print(f"🚀 正在生成 {num_lines:,} 行数据...")

    # 使用缓冲写入以提高性能 (1MB buffer)
    with open(file_path, 'w', buffering=1024*1024, encoding='utf-8') as f:
        for i in range(1, num_lines + 1):
            # 1. 构造基础坐标 (模拟聚类中心，范围 0-5000)
            base_x = random.randint(0, 5000)
            base_y = random.randint(0, 5000)

            # 2. 构造 group_name 为 "x_y"
            group_name = f"{base_x}_{base_y}"

            # 3. 构造偏移量 (在基础坐标上增加 -500 到 500 的偏移)
            # 这样数据既有随机性，又有一定的空间聚集性，适合测试 KDTree 性能
            x_offset = base_x + random.randint(-500, 500)
            y_offset = base_y + random.randint(-500, 500)

            # 4. 构造其他字段
            mark = random.choice([0,1])
            mark_path = f"/data/marks/{i}.json"
            choose = random.choice([0,1])

            # 5. 组装行数据
            line = f"{i} {group_name} {mark} {mark_path} {x_offset} {y_offset} {choose}\n"
            f.write(line)

            # 进度打印
            if i % 200000 == 0:
                print(f"   已生成: {i:,} 行")

    print(f"✅ 生成完成！文件路径: {os.path.abspath(file_path)}")
    print(f"   文件大小: {os.path.getsize(file_path) / (1024*1024):.2f} MB")

if __name__ == "__main__":
    # 默认生成 100 万行
    generate_large_dataset("group_info.txt", num_lines=1_000_000)