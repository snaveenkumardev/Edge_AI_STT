import { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  ScrollView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import { AudioManager, AudioRecorder } from "react-native-audio-api";
import { models, useLLM, useSpeechToText } from "react-native-executorch";
import ButtonUI from "./component/ButtonUI";
import { verifyTranscriptAndInvokeToolIfRequire } from "./utils/tool_selection_and_invoker";

const AUDIO_SAMPLE_RATE = 16000;
const LIVE_BUFFER_LENGTH = 1600;
const audioAsset = require("@/assets/audio/audio.mp3");

interface TranscriptsType {
  user: string
}

export default function VoiceDetector() {
  const [transcripts, setTranscripts] = useState<TranscriptsType[] | []>([]);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [processError, setProcessError] = useState<string | null>(null);
  const [isLiveVoiceTranscriptionActive, setIsLiveVoiceTranscriptionActive] = useState(false);


  // Live audio stream
  const [text, setText] = useState('');
  const [recorder] = useState(() => new AudioRecorder());

  // Model initialization
  const stt = useSpeechToText({ model: models.speech_to_text.whisper_tiny_en(), vad: models.vad.fsmn_vad(), });
  const toolSelectorModel = useLLM({ model: models.llm.hammer2_1_0_5b({ quant: true }) });

  // Ref
  const isRecordingRef = useRef(false);
  const liveVoiceTranscriptRef = useRef("")
  const transcriptVerifierIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const transcriptStartPositionRef = useRef(0);



  // Live microphone transcription: feeds raw audio chunks into the streaming
  // decoder as they arrive, separate from the file-based flow above.
 const startLiveVoiceTranscripter = async () => {
    isRecordingRef.current = true;
    setText('');

    const permission = await AudioManager.requestRecordingPermissions();
    if (permission !== 'Granted') {
      setProcessError('Microphone permission was not granted.');
      return;
    }

    // 2. Capture microphone input
    recorder.onAudioReady(
      { sampleRate: 16000, bufferLength: 1600, channelCount: 1 },
      (chunk) => {
        stt.streamInsert(chunk.buffer.getChannelData(0))
      }
    );

    await recorder.start();
    setIsLiveVoiceTranscriptionActive(true);
    transcriptVerifierIntervalRef.current = setInterval(() => {
      userVoiceTranscriptVerifier();
    }, 5000);

    // 3. Process the stream with VAD enabled
    try {
      let finalizedText = '';
      const streamIter = stt.stream({
        verbose: false,
        useVAD: true, // Enable VAD filter
        vadDetectionMargin: 500, // Wait for 500ms of silence before committing
      });

      for await (const { committed, nonCommitted } of streamIter) {
        // console.log(committed, nonCommitted,  'res')
        if (!isRecordingRef.current) break;

        if (committed.text) {
          finalizedText += committed.text;
          liveVoiceTranscriptRef.current = liveVoiceTranscriptRef.current + committed.text;
        }
        // setText(finalizedText + nonCommitted.text);
      }
    } catch (error) {
      console.error('Streaming error:', error);
    }
  };

  const stopLiveVoiceTranscripter = () => {
    isRecordingRef.current = false;
    setIsLiveVoiceTranscriptionActive(false);
    recorder.stop();
    stt.streamStop();

    if (transcriptVerifierIntervalRef.current) {
      clearInterval(transcriptVerifierIntervalRef.current);
      transcriptVerifierIntervalRef.current = null;
    }
  };


  useEffect(() => {
    return () => {
      if (transcriptVerifierIntervalRef.current) {
        clearInterval(transcriptVerifierIntervalRef.current);
      }
    };
  }, []);

  const userVoiceTranscriptVerifier = ()=> {
    // No transcript
    const trimmedLiveVoiceTranscript = liveVoiceTranscriptRef.current.trim()
    if (!trimmedLiveVoiceTranscript) {
      return;
    }
    // Transcript available
    console.log(trimmedLiveVoiceTranscript, "Voice to Transcript")
    const slicedTranscript = trimmedLiveVoiceTranscript.slice(transcriptStartPositionRef.current)
    transcriptStartPositionRef.current = trimmedLiveVoiceTranscript.length;
    setTranscripts((prevTranscripts)=> {
      return [...prevTranscripts, {user: slicedTranscript}]
    })
    verifyTranscriptAndInvokeToolIfRequire(slicedTranscript, toolSelectorModel)
  }


  // Audio file based transcript
  // const handleTranscribeFile = async () => {
  //   if (!stt.isReady || stt.isGenerating) return;

  //   setIsTranscribing(true);
  //   setProcessError(null);
  //   setTranscript("");

  //   try {
  //     const audioContext = new AudioContext({ sampleRate: AUDIO_SAMPLE_RATE });
  //     const decoded = await audioContext.decodeAudioData(audioAsset);
  //     const waveform = decoded.getChannelData(0);

  //     const result = await stt.transcribe(waveform);
  //     setTranscript(result.text);
  //   } catch (err) {
  //     setProcessError(err instanceof Error ? err.message : String(err));
  //   } finally {
  //     setIsTranscribing(false);
  //   }
  // };

  if (stt.error || toolSelectorModel.error) {
    return (
      <View style={styles.container}>
        {stt.error && <Text>Failed to load model: {stt.error.message}</Text>}
        {toolSelectorModel.error && (
          <Text>Failed to load model: {toolSelectorModel.error.message}</Text>
        )}
      </View>
    );
  }

  if (!stt.isReady || !toolSelectorModel.isReady) {
    return (
      <View style={styles.container}>
        <ActivityIndicator />
        {!stt.isReady && (
          <Text>
            Downloading Whisper Tiny (EN) model…{" "}
            {Math.round(stt.downloadProgress * 100)}%
          </Text>
        )}
        {!toolSelectorModel.isReady && (
          <Text>
            Downloading Hammer 2.1 model…{" "}
            {Math.round(toolSelectorModel.downloadProgress * 100)}%
          </Text>
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      {isLiveVoiceTranscriptionActive && (
        <>
         <View style={styles.liveStatusBanner}>
          <Text style={styles.liveStatusText}>Live Voice Transcript Activated</Text>
          <TouchableOpacity style={styles.stopButton} onPress={stopLiveVoiceTranscripter}>
            <Text style={styles.stopButtonLabel}>Stop</Text>
          </TouchableOpacity>
        </View>
        <View style= {{flex: 1, marginTop: 10, ...styles.liveStatusBanner, alignItems: 'flex-start'}}>
          <ScrollView>
            {/* {
              transcripts.map((transcript))
            } */}
          </ScrollView>

        </View>
        </>
       
      )}
     
      {
        !isLiveVoiceTranscriptionActive && (
          <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center' }}>
            <ButtonUI onPress={startLiveVoiceTranscripter} />
          </View>
        )
      }
     
      {/* <Text style={styles.title}>Voice Detector</Text>
      <Text style={styles.subtitle}>On-device via Whisper Tiny (EN)</Text>

      <Text style={styles.subtitle}>Live microphone transcription</Text>

      <Button
        title={isRecordingRef.current ? "Stop Live Transcription" : "Start Live Transcription"}
        onPress={isRecordingRef.current ? stopLiveStreaming : startLiveStreaming}
      />
      <Button
        title={"Live Voice Transcript"}
        onPress={userVoiceTranscriptVerifier}
      />
       
      <Text>Transcribe Response</Text>
      <Text>{text}</Text> */}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 10
    // alignItems: "center",
    // justifyContent: "center",
  },
  safetyModeContainer: {
    width: 100,
    height: 100,
    borderRadius: 100,
    backgroundColor: 'green'
  },
  safetyModeBtnLabel: {
    fontSize: 20,
    color: 'white'
  },
  liveStatusBanner: {
    width: '100%',
    borderWidth: 1,
    borderColor: 'green',
    borderRadius: 10,
    padding: 12,
    alignItems: 'center',
  },
  liveStatusText: {
    fontSize: 16,
    color: 'green',
    fontWeight: '600',
  },
  stopButton: {
    marginTop: 10,
    borderRadius: 6,
    paddingVertical: 8,
    paddingHorizontal: 20,
    backgroundColor: 'red',
  },
  stopButtonLabel: {
    fontSize: 16,
    color: 'white',
    fontWeight: '600',
  }
  // title: { fontSize: 20, fontWeight: "600" },
  // subtitle: { fontSize: 13, color: "#666" },
  // error: { color: "#c00" },
  // result: {
  //   width: "100%",
  //   maxHeight: 200,
  //   padding: 12,
  //   borderRadius: 8,
  //   backgroundColor: "#f2f2f2",
  // },
  // divider: {
  //   width: "100%",
  //   height: 1,
  //   backgroundColor: "#ddd",
  //   marginVertical: 8,
  // },
  // nonCommitted: { color: "#888" },
});
