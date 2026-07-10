import { useState } from "react";
import {
    ActivityIndicator,
    Button,
    ScrollView,
    StyleSheet,
    Text,
    View,
} from "react-native";
import { AudioContext } from "react-native-audio-api";
import { models, useSpeechToText } from "react-native-executorch";

const AUDIO_SAMPLE_RATE = 16000;
const audioAsset = require("@/assets/audio/audio.mp3");

export default function VoiceDetector() {
  const [transcript, setTranscript] = useState("");
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [processError, setProcessError] = useState<string | null>(null);

  const stt = useSpeechToText({ model: models.speech_to_text.whisper_tiny_en() });

  const handleTranscribeFile = async () => {
    if (!stt.isReady || stt.isGenerating) return;

    setIsTranscribing(true);
    setProcessError(null);
    setTranscript("");

    try {
      const audioContext = new AudioContext({ sampleRate: AUDIO_SAMPLE_RATE });
      const decoded = await audioContext.decodeAudioData(audioAsset);
      const waveform = decoded.getChannelData(0);

      const result = await stt.transcribe(waveform);
      setTranscript(result.text);
    } catch (err) {
      setProcessError(err instanceof Error ? err.message : String(err));
    } finally {
      setIsTranscribing(false);
    }
  };

  if (stt.error) {
    return (
      <View style={styles.container}>
        <Text>Failed to load model: {stt.error.message}</Text>
      </View>
    );
  }

  if (!stt.isReady) {
    return (
      <View style={styles.container}>
        <ActivityIndicator />
        <Text>
          Downloading Whisper Tiny (EN) model…{" "}
          {Math.round(stt.downloadProgress * 100)}%
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Voice Detector</Text>
      <Text style={styles.subtitle}>On-device via Whisper Tiny (EN)</Text>

      <Button
        title={isTranscribing ? "Transcribing…" : "Transcribe audio.mp3"}
        onPress={handleTranscribeFile}
        disabled={isTranscribing}
      />

      {!!processError && <Text style={styles.error}>{processError}</Text>}

      {!!transcript && (
        <ScrollView style={styles.result}>
          <Text>{transcript}</Text>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 12,
    padding: 24,
  },
  title: { fontSize: 20, fontWeight: "600" },
  subtitle: { fontSize: 13, color: "#666" },
  error: { color: "#c00" },
  result: {
    width: "100%",
    maxHeight: 200,
    padding: 12,
    borderRadius: 8,
    backgroundColor: "#f2f2f2",
  },
});
