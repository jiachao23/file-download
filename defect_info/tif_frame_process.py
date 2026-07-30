import tifffile
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import defaultdict

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
            sample_infos_map[s.id] = sorted_infos  # 使用业务 ID

        sample_ids = list(sample_infos_map.keys())

        with tifffile.TiffFile(tif_path) as tif:
            total_pages = len(tif.pages)

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

                # 3. 使用 TiffWriter 逐帧追加写入（兼容变尺寸、无需预分配内存）
                with tifffile.TiffWriter(save_path) as tif_writer:
                    for frame_no, info_obj in frames_in_batch:
                        if frame_no < total_pages:
                            # 惰性读取：只在需要时才解码当前帧
                            frame_array = tif.pages[frame_no].asarray()

                            # 第一帧正常写入，后续帧追加写入
                            is_first_frame = (new_frame_offset == 0)
                            tif_writer.write(
                                frame_array,
                                append=not is_first_frame
                            )

                            # 更新 ImageInfo 的帧号索引
                            info_obj.no = new_frame_offset
                            new_frame_offset += 1

                            # 及时释放当前帧内存
                            del frame_array

                # 4. 更新该批次所有 Sample 的 tif_path
                for sid in batch_sample_ids:
                    sample_obj = self.id_to_sample.get(sid)
                    if sample_obj:
                        sample_obj.tif_path = save_path

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