import numpy as np
import os
from PIL import Image
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import defaultdict
import gc

# 假设你的类定义如下
class ImageInfo:
    def __init__(self, no: int, type: int):
        self.no = no
        self.type = type

class Sample:
    def __init__(self, id: int, tif_path: str, image_info: list):
        self.id = id
        self.tif_path = tif_path
        self.image_info = image_info  # List[ImageInfo]

class TifFrameProcessor:
    def __init__(self, output_dir="output_tifs", samples_per_batch=100):
        self.output_dir = output_dir
        self.samples_per_batch = samples_per_batch
        os.makedirs(self.output_dir, exist_ok=True)
        self.id_to_sample = {}

    def process_single_file(self, tif_path, samples):
        if not samples:
            return

        # 1. 建立映射：{sample_id: [ImageInfo_1, ImageInfo_2, ...]}
        sample_infos_map = {}
        for s in samples:
            sorted_infos = sorted(s.image_info, key=lambda x: x.no)
            sample_infos_map[s.id] = sorted_infos

        sample_ids = list(sample_infos_map.keys())

        # 使用 Pillow 打开 TIF 文件
        img = Image.open(tif_path)
        total_pages = getattr(img, "n_frames", 1)  # 获取总帧数

        try:
            # 2. 按 Sample 数量切分批次
            for i in range(0, len(sample_ids), self.samples_per_batch):
                batch_sample_ids = sample_ids[i : i + self.samples_per_batch]

                # 收集当前批次所有的帧号及对应的 ImageInfo 对象
                frames_in_batch = []
                for sid in batch_sample_ids:
                    for info_obj in sample_infos_map[sid]:
                        frames_in_batch.append((info_obj.no, info_obj))

                # 按原始帧号排序，保证写入顺序
                frames_in_batch.sort(key=lambda x: x[0])

                # 定义当前批次的输出路径
                save_name = f"{os.path.basename(tif_path).replace('.tif', '')}_batch_{i//self.samples_per_batch}.tif"
                save_path = os.path.join(self.output_dir, save_name)

                new_frame_offset = 0
                pil_frames = []  # 用于收集当前批次的 PIL Image 对象

                # 3. 逐帧读取并收集
                for frame_no, info_obj in frames_in_batch:
                    if frame_no < total_pages:
                        # 跳转到指定帧
                        img.seek(frame_no)
                        # 转换为 RGB 或 L (灰度) 模式，并拷贝一份防止原图被修改
                        # 注意：如果原图是 CMYK 或其他模式，建议统一转为 RGB
                        frame_img = img.convert("RGB").copy()

                        pil_frames.append(frame_img)

                        # 更新 ImageInfo 的帧号索引
                        info_obj.no = new_frame_offset
                        new_frame_offset += 1

                # 4. 批量保存为多页 TIF
                if pil_frames:
                    first_frame = pil_frames[0]
                    rest_frames = pil_frames[1:] if len(pil_frames) > 1 else []

                    # save_all=True 会将 append_images 中的图像作为后续页面写入
                    first_frame.save(
                        save_path,
                        format="TIFF",
                        save_all=True,
                        append_images=rest_frames
                    )

                    # 更新该批次所有 Sample 的 tif_path
                    for sid in batch_sample_ids:
                        sample_obj = self.id_to_sample.get(sid)
                        if sample_obj:
                            sample_obj.tif_path = save_path

                # 及时释放内存
                del pil_frames
                gc.collect()

        finally:
            img.close()  # 确保文件句柄被正确关闭

        print(f"✅ Finished processing: {tif_path}")

def split_large_tif(samples_list, max_workers=8, samples_per_batch=100):
    processor = TifFrameProcessor(samples_per_batch=samples_per_batch)

    # 建立全局 业务ID 到对象的映射
    processor.id_to_sample = {s.id: s for s in samples_list}

    # 按 tif_path 分组
    grouped_samples = defaultdict(list)
    for sample in samples_list:
        grouped_samples[sample.tif_path].append(sample)

    with ThreadPoolExecutor(max_workers=max_workers) as executor:
        futures = [
            executor.submit(processor.process_single_file, tif_path, samples)
            for tif_path, samples in grouped_samples.items()
        ]
        for future in as_completed(futures):
            future.result()