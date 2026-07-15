import { useState } from 'react';
import { ActivityIndicator, Alert, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import { AudioContext, AudioManager, AudioRecorder, FileFormat } from 'react-native-audio-api';
import { LLMTool, Message, models, useLLM, useSpeechToText } from 'react-native-executorch';

const AUDIO_SAMPLE_RATE = 16000;

const SYSTEM_PROMPT =
  `You are a safety-triage assistant for a mobile app. The user's message is a speech-to-text transcript of what they said out loud, not typed text — expect missing punctuation, mistranscribed or dropped words, run-on phrasing, and background-noise artifacts. Infer the user's intent from the likely spoken meaning rather than the literal transcript, and do not treat transcription noise as the user's actual request.

Rules:
1. Call a tool only if the (inferred) situation clearly matches its description. Do not call a tool "just in case".
2. If the situation involves danger, injury, threat, or the user being unsafe/unable to help themselves, prefer a safety/emergency-labeled tool over any other tool, even if another tool is a partial match.
3. If no tool applies, return an empty list — do not invent a tool or answer conversationally.
4. If required tool parameters are missing or garbled in the transcript, still call the tool with the parameters you can infer; do not ask a follow-up question.
5. Respond with ONLY a JSON array of tool calls in the form [{"name": "...", "arguments": {...}}], and nothing else — no explanation, no markdown.`;

const TOOL_DEFINITIONS: LLMTool[] = [
  {
    name: 'emergency_helper',
    description: "SAFETY-CRITICAL. Use whenever the user reports danger, injury, being lost, feeling unsafe, threats, or needing urgent help. Takes priority over all other tools when the situation is ambiguous.",
    // description: 'start the record',
    parameters: {},
  },
];

type ToolObj = {
    arguments: {},
    name: string
}

type ToolResponse = ToolObj[] | []

export default function LiveTranscriber() {
 
  // Model initialization
  const speechToTextModel = useSpeechToText({
    model: models.speech_to_text.whisper_tiny_en(),
  });
  const toolSelectionModel = useLLM({ model: models.llm.hammer2_1_0_5b({ quant: true }) });

  const [text, setText] = useState("");
  const [isRecording, setIsRecording] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [isSelectingTool, setIsSelectingTool] = useState(false);
  const [toolResponse, setToolResponse] = useState('');
  const [processError, setProcessError] = useState<string | null>(null);
  const [recorder] = useState(() => new AudioRecorder());

  const startRecording = async () => {
    setProcessError(null);
    setText('');

    const permission = await AudioManager.requestRecordingPermissions();
    if (permission !== 'Granted') {
      setProcessError('Microphone permission was not granted.');
      return;
    }

    recorder.enableFileOutput({
      format: FileFormat.Wav,
      channelCount: 1,
    });

    await recorder.start();
    setIsRecording(true);
  };

  const selectToolFromTranscript = async (transcript: string) => {
    const trimmedTranscript = transcript.trim();

    if (!trimmedTranscript) {
      Alert.alert("No Input", "No Input")
      return;
    };
    console.log('Tool selection')
    setIsSelectingTool(true);
    try {
      const userPrompt = trimmedTranscript;
      console.log(userPrompt, 'prompt')
      const chat: Message[] = [
        { role: 'system', content: SYSTEM_PROMPT },
        { role: 'user', content: userPrompt },
      ];
      const response = await toolSelectionModel.generate(chat, TOOL_DEFINITIONS);
      const parsedToolResponse = parseToolCalls(response);
      console.log(parsedToolResponse, 'tool response')
      toolInvoker(parsedToolResponse)
    //   setToolResponse(response);
    // toolInvoker(response)
    } catch (error) {
      console.log("Error", error)
      setProcessError(error instanceof Error ? error.message : String(error));
    } finally {
      setIsSelectingTool(false);
    }
  };

  const stopRecordingAndTranscribe = async () => {
    setIsRecording(false);
    setToolResponse('');

    try {
      const stopResult = await recorder.stop();
      console.log("Record Stopped")
      if (stopResult.status === 'error') throw new Error(stopResult.message);

      const filePath = stopResult.paths[0];
      if (!filePath) throw new Error('Recording produced no file.');

      setIsTranscribing(true);

      const audioContext = new AudioContext({ sampleRate: AUDIO_SAMPLE_RATE });
      const decoded = await audioContext.decodeAudioData(filePath);
      const waveform = decoded.getChannelData(0);

      const result = await speechToTextModel.transcribe(waveform);
      console.log(result, 'result')
      setText(result.text);
      setIsTranscribing(false);

      await selectToolFromTranscript(result.text);
    } catch (error) {
      setProcessError(error instanceof Error ? error.message : String(error));
    } finally {
      setIsTranscribing(false);
    }
  };

    function parseToolCalls(raw: string) {
        const cleaned = raw
            .trim()
            .replace(/^```(?:json)?\s*/i, "") // strip opening ``` or ```json
            .replace(/```$/, "")              // strip closing ```
            .trim();
        return JSON.parse(cleaned);
    }

  function toolInvoker(toolResponse: ToolResponse) {
    if (toolResponse.length === 0) {
      Alert.alert("Tool", "No Tool selected");
      return;
    };

    const tool = toolResponse[0];
    if (tool.name === 'emergency_helper') {
      emergencyTool();
    } else {
      Alert.alert("Tool", "No Tool selected")
    }
  }

  function emergencyTool() {
    Alert.alert("I understand, You're in Emergency situation. I will help you to resolve.")
  }

   if (speechToTextModel?.error || toolSelectionModel?.error) {
      return (
        <View style={styles.container}>
            {speechToTextModel?.error && <Text>Failed to load model: {speechToTextModel.error.message}</Text>}
            {toolSelectionModel?.error && <Text>Failed to load model: {toolSelectionModel.error.message}</Text>} 
        </View>
      );
    }
  
    if (!speechToTextModel.isReady || !toolSelectionModel.isReady) {
      return (
        <View style={styles.container}>
          <ActivityIndicator />
          {!speechToTextModel.isReady && (
            <Text>
              Downloading Whisper Tiny (EN) model…{" "}
              {Math.round(speechToTextModel.downloadProgress * 100)}%
            </Text>
          )}
          {!toolSelectionModel.isReady && (
            <Text>
              Downloading Hammer 2.1 model…{" "}
              {Math.round(toolSelectionModel.downloadProgress * 100)}%
            </Text>
          )}
        </View>
      );
    }

//   return (
//     <View style={styles.container}>
//       <Text>{text || 'Press start, speak, then stop to transcribe...'}</Text>

//       {!!processError && <Text style={styles.error}>{processError}</Text>}
//         <Button
//           onPress={isRecording  ? stopRecordingAndTranscribe : startRecording}
//           title={isRecording ? "Stop & Transcribe" : "Start Recording"}
//           disabled={speechToTextModel.isGenerating || isTranscribing}
//         />

//       {isTranscribing && (
//         <>
//           <ActivityIndicator />
//           <Text>Transcribing…</Text>
//         </>
//       )}

//       {isSelectingTool && (
//         <>
//           <ActivityIndicator />
//           <Text>Selecting tool…</Text>
//         </>
//       )}

//       {!!toolResponse && <Text style={styles.result}>{toolResponse}</Text>}
//     </View>
//   );
  return (
    <View style= {{flex: 1, padding: 10}}>
        {
            (isRecording || isTranscribing || isSelectingTool) && (
                  <View style={styles.currentStatus}>
                      <Text style={styles.statusText}>
                          {isRecording && "Recording in-progress"}
                          {isTranscribing && "Transcribe in-progress"}
                          {isSelectingTool && "Tool selection in-progress"}
                      </Text>
                  </View>
            )
        }
      
          {
              !!text && (
                  <View style={{marginTop: 10, borderWidth: 1, borderColor: 'green', paddingHorizontal: 10, paddingVertical: 20, borderRadius: 10}}>
                      <Text style={{color: 'black', fontSize: 16}}>{"Transcribe Response: " + text}</Text>
                  </View>
              )
          }
       
        <View style= {{flex: 1, justifyContent: 'center', alignItems: 'center'}}>
            <TouchableOpacity style= {[styles.startAndStopBtnContainer, {backgroundColor: isRecording ? 'red' : 'green'}]} onPress={isRecording  ? stopRecordingAndTranscribe : startRecording}>
                <Text style= {styles.startAndStopBtnLabel}>{isRecording  ? 'Stop ' : 'Start '}Record</Text>
            </TouchableOpacity>
            
        </View>

    </View>
  )
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    // alignItems: "center",
    // justifyContent: "center",
    // gap: 12,
    // padding: 24,
  },
  error: { color: "#c00" },
  result: {
    width: "100%",
    padding: 12,
    borderRadius: 8,
    backgroundColor: "#f2f2f2",
  },
  currentStatus: {
    borderWidth: 1,
    borderColor: 'green',
    padding: 10,
    justifyContent: 'center',
    alignItems: 'center',
    height: 70,
    borderRadius: 10
  },
  statusText: {
    fontSize: 20,
    color: 'black'
  },
  startAndStopBtnContainer: {
    width: 220,
    height: 220,
    borderRadius: 220,
    justifyContent: 'center',
    alignItems: 'center',
    padding: 10,
    borderWidth: 1,
    borderColor: 'black'
  },
  startAndStopBtnLabel: {
    fontSize: 30,
    color: 'white',
    textAlign: 'center'
  }
});
