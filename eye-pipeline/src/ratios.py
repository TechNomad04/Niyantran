import mediapipe as mp
import cv2
import numpy as np
from mediapipe.tasks import python
from mediapipe.tasks.python import vision
from PyEMD import EMD

class RatioCalculator:
    def __init__(self, model_path='face_landmarker.task'):
        BaseOptions = mp.tasks.BaseOptions
        FaceLandmarker = mp.tasks.vision.FaceLandmarker
        FaceLandmarkerOptions = mp.tasks.vision.FaceLandmarkerOptions
        VisionRunningMode = mp.tasks.vision.RunningMode

        self.options = FaceLandmarkerOptions(
            base_options = BaseOptions(model_asset_path=model_path),
            running_mode = VisionRunningMode.VIDEO
        )

        self.detector = FaceLandmarker.create_from_options(self.options)

    def get_landmarks(self, frame, timestamp_ms: int):
        h, w, _ = frame.shape
        rgb_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb_frame)
        result = self.detector.detect_for_video(mp_image, timestamp_ms)
        return result.face_landmarks

    def get_left_eye_ratio(self, landmarks):
        upper = np.array([landmarks[159].x, landmarks[159].y])
        lower = np.array([landmarks[145].x, landmarks[145].y])

        outer = np.array([landmarks[33].x, landmarks[33].y])
        inner = np.array([landmarks[133].x, landmarks[133].y])

        vertical_dist = np.linalg.norm(upper-lower)
        horizontal_dist = np.linalg.norm(outer-inner)

        return vertical_dist/horizontal_dist

    def get_right_eye_ratio(self, landmarks):
        upper = np.array([landmarks[386].x, landmarks[386].y])
        lower = np.array([landmarks[374].x, landmarks[374].y])

        outer = np.array([landmarks[263].x, landmarks[263].y])
        inner = np.array([landmarks[362].x, landmarks[362].y])

        vertical_dist = np.linalg.norm(upper-lower)
        horizontal_dist = np.linalg.norm(outer-inner)

        return vertical_dist/horizontal_dist

    def extractIMF(self, IPH_ratios):
        T = np.arange(len(IPH_ratios))
        S = np.array(IPH_ratios)
        emd = EMD()
        imfs = emd.emd(S, max_imf=4)

        residue = S - np.sum(imfs, axis=0)
        features = np.vstack([imfs, residue.reshape(1, -1)])

      
        target_rows = 5
        if features.shape[0] < target_rows:
            padding = np.zeros((target_rows - features.shape[0], features.shape[1]))
            features = np.vstack([features, padding])

        return features.T

