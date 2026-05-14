import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, NativeModules } from 'react-native';

const { AppBlockerModule } = NativeModules;

interface WarningScreenProps {
  packageName: string;
  onWhitelist: () => void;
}

export const WarningScreen: React.FC<WarningScreenProps> = ({ packageName, onWhitelist }) => {
  const [stage, setStage] = useState(1); // 1 = 10s wait, 2 = 5s wait
  const [timeLeft, setTimeLeft] = useState(10);

  useEffect(() => {
    let timer: ReturnType<typeof setTimeout>;
    if (timeLeft > 0) {
      timer = setTimeout(() => setTimeLeft(timeLeft - 1), 1000);
    }
    return () => clearTimeout(timer);
  }, [timeLeft]);

  const handleContinue = () => {
    if (stage === 1) {
      setStage(2);
      setTimeLeft(5);
    } else {
      // Allow app
      AppBlockerModule.whitelistApp(packageName);
      onWhitelist();
      AppBlockerModule.goHome(); // Let them go to home then reopen it, or we can just send it to back
    }
  };

  const handleClose = () => {
    AppBlockerModule.goHome();
  };

  return (
    <View style={styles.container}>
      <View style={styles.card}>
        <Text style={styles.warningIcon}>⚠️</Text>
        <Text style={styles.title}>RESTRICTED APP</Text>
        
        <Text style={styles.message}>
          {stage === 1 
            ? "Are you sure you want to do this? This app is blocked for your productivity." 
            : "For real bro? Think about your goals."}
        </Text>

        <TouchableOpacity 
          style={styles.closeButton} 
          onPress={handleClose}
          activeOpacity={0.8}
        >
          <Text style={styles.closeButtonText}>CLOSE APP</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.continueButton, timeLeft > 0 && styles.continueButtonDisabled]} 
          onPress={handleContinue}
          disabled={timeLeft > 0}
          activeOpacity={0.8}
        >
          <Text style={styles.continueButtonText}>
            {timeLeft > 0 ? `CONTINUE IN ${timeLeft}S` : 'CONTINUE ANYWAY'}
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#0B0F19',
    justifyContent: 'center',
    padding: 20,
  },
  card: {
    backgroundColor: 'rgba(15, 23, 42, 0.9)',
    borderRadius: 20,
    padding: 30,
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#EF4444',
    shadowColor: '#EF4444',
    shadowOffset: { width: 0, height: 10 },
    shadowOpacity: 0.3,
    shadowRadius: 20,
    elevation: 10,
  },
  warningIcon: {
    fontSize: 50,
    marginBottom: 20,
  },
  title: {
    color: '#EF4444',
    fontSize: 24,
    fontWeight: 'bold',
    letterSpacing: 2,
    marginBottom: 20,
    textAlign: 'center',
  },
  message: {
    color: '#E2E8F0',
    fontSize: 16,
    textAlign: 'center',
    marginBottom: 40,
    lineHeight: 24,
  },
  closeButton: {
    backgroundColor: 'rgba(239, 68, 68, 0.15)',
    borderWidth: 1,
    borderColor: '#EF4444',
    paddingVertical: 16,
    paddingHorizontal: 40,
    borderRadius: 12,
    width: '100%',
    alignItems: 'center',
    marginBottom: 15,
  },
  closeButtonText: {
    color: '#EF4444',
    fontSize: 16,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
  continueButton: {
    backgroundColor: 'rgba(148, 163, 184, 0.1)',
    borderWidth: 1,
    borderColor: '#94A3B8',
    paddingVertical: 16,
    paddingHorizontal: 40,
    borderRadius: 12,
    width: '100%',
    alignItems: 'center',
  },
  continueButtonDisabled: {
    opacity: 0.5,
  },
  continueButtonText: {
    color: '#94A3B8',
    fontSize: 14,
    fontWeight: 'bold',
    letterSpacing: 1,
  },
});
