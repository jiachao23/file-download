import tifffile
import numpy as np
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from collections import defaultdict
import gc

class TifFrameProcessor:
    def __init__(self, output_dir="output_tifs", samples_per_batch=100):
        self.output_dir = output_dir
        self.samples_per_batch = samples_per_batch
        os.makedirs(self.output_dir, exist_ok=True)
        # 用于在多线程中安全更新原始 Sample 对象的映射
        self.id_to_sample = {}

    def process_single_file(self, tif_path, samples):
        """
        处理单个 TIF 文件，严格保证每个 Sample 的帧不被拆分
        """
        if not samples:
            return

        # 1. 建立当前批次内 Sample 对象与帧号的映射
        # 结构: {id(sample_obj): [frame_no_1, frame_no_2, ...]}
        sample_frames_map = {}
        for s in samples:
            sample_frames_map.setdefault(id(s), []).append(s.nos)

        sample_ids = list(sample_frames_map.keys())
        total_samples = len(sample_ids)

        with tifffile.TiffFile(tif_path) as tif:
            total_pages = len(tif.pages)

            # 获取单帧元数据
            first_frame_no = sample_frames_map[sample_ids[0]][0]
            if first_frame_no >= total_pages: return
            first_frame = tif.pages[first_frame_no].asarray()
            frame_shape = first_frame.shape
            frame_dtype = first_frame.dtype

            # 2. 严格按 Sample 数量切分批次
            for i in range(0, total_samples, self.samples_per_batch):
                batch_sample_ids = sample_ids[i : i + self.samples_per_batch]

                # 收集当前批次所有的帧号
                frames_in_batch = []
                for sid in batch_sample_ids:
                    frames_in_batch.extend(sorted(sample_frames_map[sid]))

                actual_frame_count = len(frames_in_batch)
                new_data = np.zeros((actual_frame_count, *frame_shape), dtype=frame_dtype)

                valid_count = 0
                new_frame_offset = 0

                # 3. 顺序读取并更新 Sample 对象
                for frame_no in frames_in_batch:
                    if frame_no < total_pages:
                        new_data[valid_count] = tif.pages[frame_no].asarray()

                        # 找到该帧所属的 Sample 并更新其索引
                        for sid in batch_sample_ids:
                            if frame_no in sample_frames_map[sid]:
                                sample_obj = self.id_to_sample.get(sid)
                                if sample_obj:
                                    save_name = f"{os.path.basename(tif_path).replace('.tif', '')}_batch_{i//self.samples_per_batch}.tif"
                                    save_path = os.path.join(self.output_dir, save_name)

                                    # 更新原始对象
                                    sample_obj.tif_path = save_path
                                    # 如果 nos 是单值，转为列表；如果已经是列表，则追加
                                    if isinstance(sample_obj.nos, list):
                                        sample_obj.nos.append(new_frame_offset)
                                    else:
                                        sample_obj.nos = [new_frame_offset]
                        valid_count += 1
                    new_frame_offset += 1

                if valid_count == 0: continue
                if valid_count < actual_frame_count:
                    new_data = new_data[:valid_count]

                # 4. 写入 memmap
                save_name = f"{os.path.basename(tif_path).replace('.tif', '')}_batch_{i//self.samples_per_batch}.tif"
                save_path = os.path.join(self.output_dir, save_name)

                out_memmap = tifffile.memmap(save_path, shape=new_data.shape, dtype=frame_dtype)
                out_memmap[:] = new_data

                del new_data, out_memmap
                gc.collect()

        print(f"✅ Finished processing: {tif_path}")

def split_large_tif(samples_list, max_workers=8, samples_per_batch=100):
    processor = TifFrameProcessor(samples_per_batch=samples_per_batch)

    # 建立全局 id 到对象的映射
    processor.id_to_sample = {id(s): s for s in samples_list}

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