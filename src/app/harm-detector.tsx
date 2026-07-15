import { useState } from "react";
import {
  ActivityIndicator,
  Button,
  StyleSheet,
  Text,
  TextInput,
  View,
} from "react-native";
import { LLMTool, models, useLLM } from "react-native-executorch";
import { verifyTranscriptAndInvokeToolIfRequire } from "./utils/tool_selection_and_invoker";

const SYSTEM_PROMPT =
  `You're an intelligent Safety assistant. You can helps to do a safety action if it is required. You're provided with set of tools. Those tools can perform a safety action. So you're responsibility is select a suitable tool for safety action`;

export default function HarmDetector() {
  const [input, setInput] = useState("");
  const llm = useLLM({ model: models.llm.hammer2_1_0_5b({ quant: true }) });

  const TOOL_DEFINITIONS: LLMTool[] = [
  {
    name: 'emergency',
    description: "Emergency situation handler. If any emergency event occurs, this can be helps to resolve it",
    // description: 'start the record',
    parameters: {},
  },
];

  const handleCheck = async () => {
  //   if (!input.trim() || !llm.isReady || llm.isGenerating) return;

  //   const chat: Message[] = [
  //     { role: "system", content: SYSTEM_PROMPT },
  //     { role: "user", content: input },
  //   ];

  //    // Chat completion - returns the generated response
  // const response = await llm.generate(chat, TOOL_DEFINITIONS);
  // console.log(parseToolCalls(response), 'response')
  // console.log('Complete response:', response);
  console.log(input)
  verifyTranscriptAndInvokeToolIfRequire(input, llm)
  };

  function parseToolCalls(raw: string) {
  const cleaned = raw
    .trim()
    .replace(/^```(?:json)?\s*/i, "") // strip opening ``` or ```json
    .replace(/```$/, "")              // strip closing ```
    .trim();
  return JSON.parse(cleaned);
}

  if (llm.error) {
    return (
      <View style={styles.container}>
        <Text>Failed to load model: {llm.error.message}</Text>
      </View>
    );
  }

  if (!llm.isReady) {
    return (
      <View style={styles.container}>
        <ActivityIndicator />
        <Text>
          Downloading Hammer 2.1 model… {Math.round(llm.downloadProgress * 100)}%
        </Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Harm Detector</Text>
      <Text style={styles.subtitle}>On-device via Hammer 2.1 (quantized)</Text>

      <TextInput
        style={styles.input}
        placeholder="Type a message to check…"
        value={input}
        onChangeText={setInput}
        multiline
      />

      <Button
        title={llm.isGenerating ? "Checking…" : "Check message"}
        onPress={handleCheck}
        disabled={llm.isGenerating || !input.trim()}
      />

      {!!llm.response && <Text style={styles.result}>{llm.response}</Text>}
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
  input: {
    width: "100%",
    minHeight: 80,
    borderWidth: 1,
    borderColor: "#ccc",
    borderRadius: 8,
    padding: 12,
    textAlignVertical: "top",
  },
  result: {
    width: "100%",
    padding: 12,
    borderRadius: 8,
    backgroundColor: "#f2f2f2",
  },
});
