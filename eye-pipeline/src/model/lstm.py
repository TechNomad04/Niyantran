import torch
import torch.nn as nn


class LSTMModel(nn.Module):
    def __init__(self, hidden_dim: int = 64, layer_dim: int = 3, input_dim: int = 10, output_dim: int = 1):
        super().__init__()
        self.hidden_dim = hidden_dim
        self.layer_dim = layer_dim
        self.lstm = nn.LSTM(input_dim, hidden_dim, layer_dim, batch_first=True, dropout=0.2)
        self.fc = nn.Linear(hidden_dim, output_dim)
        self.sigmoid = nn.Sigmoid()

    def forward(self, x):
        h0 = torch.zeros(self.layer_dim, x.size(0), self.hidden_dim).to(x.device)
        c0 = torch.zeros(self.layer_dim, x.size(0), self.hidden_dim).to(x.device)
        out, (hn, cn) = self.lstm(x, (h0.detach(), c0.detach()))
        logit = self.fc(out[:, -1, :])
        probability = self.sigmoid(logit)
        return probability


class LSTMWrapper:
    def __init__(self, weights_path: str, device: str = "cpu"):
        self.device = torch.device(device)

        checkpoint = torch.load(weights_path, map_location=self.device, weights_only=False)

        if isinstance(checkpoint, dict):
            self.model = LSTMModel()
            self.model.load_state_dict(checkpoint)
        else:
            self.model = checkpoint

        self.model.to(self.device)
        self.model.eval()

    def predict(self, features) -> float:
        x = torch.tensor(features, dtype=torch.float32).unsqueeze(0).to(self.device)
        with torch.no_grad():
            prob = self.model(x).item()
        return prob
