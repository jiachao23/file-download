import os
import shutil
import numpy as np
import tifffile

from defect_info.tif_frame_process import Sample, ImageInfo, split_large_tif


# 导入你处理逻辑所在的文件（假设你的代码在 processor.py 中）
# from processor import Sample, ImageInfo, split_large_tif
# 为了方便测试，这里假设你已经将类定义导入

def test_frame_mapping():
    print("🛠️ [1/4] 准备测试数据...")
    test_dir = "test_verify_data"

    output_dir = f"/Users/jcohy/Work/WorkSpace/IdeaProjects/file-download/defect_info/output_tifs"

    # 清理旧测试数据
    if os.path.exists(test_dir): shutil.rmtree(test_dir)
    if os.path.exists(output_dir): shutil.rmtree(output_dir)
    os.makedirs(test_dir, exist_ok=True)

    # 创建一个包含 10 帧的假 TIF 文件
    fake_tif_path = os.path.join(test_dir, "dummy_10_frames.tif")
    dummy_data = np.random.randint(0, 255, (10, 64, 64), dtype=np.uint8)
    tifffile.imwrite(fake_tif_path, dummy_data)

    # 构造模拟的 Sample 对象
    # Sample 1: 包含第 2, 5, 8 帧
    # Sample 2: 包含第 0, 9 帧
    samples = [
        Sample(id=1001, tif_path=fake_tif_path, image_info=[
            ImageInfo(no=1, type=1), ImageInfo(no=2, type=1), ImageInfo(no=3, type=1)
        ]),
        Sample(id=1002, tif_path=fake_tif_path, image_info=[
            ImageInfo(no=4, type=2), ImageInfo(no=5, type=2)
        ])
    ]

    # 记录处理前的原始帧号
    original_frames = {s.id: [info.no for info in s.image_info] for s in samples}
    print(f"原始帧号映射: {original_frames}")

    print("🚀 [2/4] 执行 TIF 拆分处理...")
    # 调用你的处理函数
    split_large_tif(samples, max_workers=1, samples_per_batch=100)

    print("🔍 [3/4] 验证处理结果...")
    all_passed = True

    for s in samples:
        # 1. 验证 tif_path 是否被更新
        if s.tif_path == fake_tif_path:
            print(f"❌ [Sample {s.id}] 失败: tif_path 未被更新！")
            all_passed = False
            continue

        # 2. 验证新文件是否存在且可读
        if not os.path.exists(s.tif_path):
            print(f"❌ [Sample {s.id}] 失败: 新文件 {s.tif_path} 不存在！")
            all_passed = False
            continue

        # 3. 验证帧号是否被正确重映射为 0, 1, 2...
        new_frames = [info.no for info in s.image_info]
        expected_frames = list(range(len(original_frames[s.id])))

        if new_frames == expected_frames:
            print(f"✅ [Sample {s.id}] 成功: 帧号已正确重映射为 {new_frames}")
        else:
            print(f"❌ [Sample {s.id}] 失败: 期望帧号 {expected_frames}, 实际帧号 {new_frames}")
            all_passed = False

        # 4. 验证新 TIF 文件的实际页数是否与 image_info 长度一致
        with tifffile.TiffFile(s.tif_path) as tif:
            actual_pages = len(tif.pages)
            if actual_pages == len(s.image_info):
                print(f"   📄 文件页数验证通过: 实际 {actual_pages} 页 == 预期 {len(s.image_info)} 页")
            else:
                print(f"   ❌ 文件页数验证失败: 实际 {actual_pages} 页 != 预期 {len(s.image_info)} 页")
                all_passed = False

    print("🏁 [4/4] 测试完成！")
    if all_passed:
        print("🎉 所有验证通过！你的代码逻辑完全正确。")
    else:
        print("⚠️ 存在验证失败的项，请检查代码逻辑。")

    # 清理测试数据
    shutil.rmtree(test_dir)
    shutil.rmtree(output_dir)

if __name__ == "__main__":
    # 确保你的 Sample, ImageInfo, split_large_tif 已经导入
    test_frame_mapping()