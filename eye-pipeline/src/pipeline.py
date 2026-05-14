import cv2
import os
import numpy as np

from src.preprocessing import standardize_video
from src.detection import EyeTracker
from src.ratios import RatioCalculator


class DryEyePipeline:
    def __init__(
        self,
        face_landmarker_path: str = "face_landmarker.task",
        processed_dir: str = "data/processed",
        lstm_wrapper=None,
        threshold: float = 0.5,
    ):
        self.processed_dir = processed_dir
        self.lstm_wrapper = lstm_wrapper
        self.threshold = threshold
        self.face_landmarker_path = face_landmarker_path

        self.eye_tracker = EyeTracker(model_path=face_landmarker_path)
        self.ratio_calculator = RatioCalculator(model_path=face_landmarker_path)

    def _extract_ear_sequence(self, video_path: str) -> tuple[list[float], list[float]]:
        cap = cv2.VideoCapture(video_path)
        left_ear_sequence = []
        right_ear_sequence = []

        fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
        frame_idx = 0

        while cap.isOpened():
            ret, frame = cap.read()
            if not ret:
                break

            timestamp_ms = int(frame_idx * (1000.0 / fps))
            landmarks_list = self.ratio_calculator.get_landmarks(frame, timestamp_ms)
            frame_idx += 1

            if landmarks_list:
                landmarks = landmarks_list[0]
                left_ear = self.ratio_calculator.get_left_eye_ratio(landmarks)
                right_ear = self.ratio_calculator.get_right_eye_ratio(landmarks)
                left_ear_sequence.append(left_ear)
                right_ear_sequence.append(right_ear)

        cap.release()
        return left_ear_sequence, right_ear_sequence

    def run(self, video_path: str) -> dict:
        standardize_video([video_path], output_dir=self.processed_dir)

        filename = os.path.basename(video_path)
        processed_path = os.path.join(self.processed_dir, filename)

        left_ear_sequence, right_ear_sequence = self._extract_ear_sequence(processed_path)

        if len(left_ear_sequence) == 0:
            return {
                "left_ear_sequence": [],
                "right_ear_sequence": [],
                "imf_features": None,
                "dry_eye_prob": None,
                "has_dry_eye": None,
            }

        left_imf_features = self.ratio_calculator.extractIMF(left_ear_sequence)
        right_imf_features = self.ratio_calculator.extractIMF(right_ear_sequence)
        imf_features = np.concatenate([left_imf_features, right_imf_features], axis=1)

        dry_eye_prob = None
        has_dry_eye = None

        if self.lstm_wrapper is not None:
            dry_eye_prob = self.lstm_wrapper.predict(imf_features)
            has_dry_eye = dry_eye_prob >= self.threshold

        return {
            "left_ear_sequence": left_ear_sequence,
            "right_ear_sequence": right_ear_sequence,
            "imf_features": imf_features,
            "dry_eye_prob": dry_eye_prob,
            "has_dry_eye": has_dry_eye,
        }

    def close(self):
        self.eye_tracker.close()
        self.ratio_calculator.detector.close()
