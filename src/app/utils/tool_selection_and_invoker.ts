import { Alert } from "react-native";
import { LLMTool, LLMType, Message } from "react-native-executorch";

const SYSTEM_PROMPT = `You are a safety-triage assistant for a mobile app. The user's message is a speech-to-text transcript of what they said out loud, not typed text — expect missing punctuation, mistranscribed or dropped words, run-on phrasing, and background-noise artifacts. Infer the user's intent from the likely spoken meaning rather than the literal transcript, and do not treat transcription noise as the user's actual request.
verify if user intent means an emergency situation and respond an appropirate tool response.

Rules:
1. Respond a tool only if the (inferred) situation clearly matches its description. Do not respond a tool "just in case".
2. If the situation involves danger, injury, threat, or the user being unsafe/unable to help themselves, prefer a safety/emergency-labeled tool over any other tool, even if another tool is a partial match.
3. If no tool applies, return an empty list — do not invent a tool or answer conversationally.
4. If required tool parameters are missing or garbled in the transcript, still call the tool with the parameters you can infer; do not ask a follow-up question.
5. Respond with ONLY a JSON array of tool calls in the form [{"name": "...", "arguments": {...}}]
6. Don't respond a Tool if user is safe and the intent don't means any emergency situation.
7. verify a very recent user intent for tool selection. existing context may a emergency situation but currently not. So check existing context with current intent and verify if tool response is required.

Example response schema:
1. user intent match a Tool Decription:
"[{name: TOOL_NAME}, argument: [Arguments]]"

user intent not match a Tool Description:
"[]"
`;
const TOOL_DEFINITIONS: LLMTool[] = [
  {
    name: "emergency_helper",
    description:
      "SAFETY-CRITICAL. Use whenever if user intent means an emergency situation and he requires an help. Emergency situation likes Accident, going to die, kill by someone, harassment, Attacked by someone, Unsafe zone and an any emergency situation. Don't use this if user not requires an Emergency help",
    parameters: {},
  },
];

type ToolObj = {
  arguments: {};
  name: string;
};

type ToolResponse = ToolObj[] | [];

type ToolInvocationResult =
  | { status: "no_tool" }
  | { status: "emergency"; message: string };

type ToolSelectionResult =
  | { status: "empty_transcript" }
  | { status: "error"; error: string }
  | {
      status: "success";
      toolResponse: ToolResponse;
      invocation: ToolInvocationResult;
    };

function parseToolCalls(raw: string) {
  const cleaned = raw
    .trim()
    .replace(/^```(?:json)?\s*/i, "") // strip opening ``` or ```json
    .replace(/```$/, "") // strip closing ```
    .trim();
  return JSON.parse(cleaned);
}

export function toolInvoker(toolResponse: ToolResponse): ToolInvocationResult {
  if (toolResponse.length === 0) {
    return { status: "no_tool" };
  }

  const tool = toolResponse[0];
  if (tool.name === "emergency_helper") {
    return emergencyTool();
  }

  return { status: "no_tool" };
}

function emergencyTool(): ToolInvocationResult {
  Alert.alert(
    "Emergency",
    "I understand, You're in Emergency situation. I will help you to resolve.",
  );
  return {
    status: "emergency",
    message:
      "I understand, You're in Emergency situation. I will help you to resolve.",
  };
}

export async function verifyTranscriptAndInvokeToolIfRequire(
  userTranscripts: Message[],
  toolSelectionModel: LLMType,
): Promise<Message> {
  try {
    const userTranscriptArr: Message[] = userTranscripts.filter(
      (transcript) => {
        return transcript.role === "user";
      },
    );
    console.log(userTranscriptArr, "user transcripts");
    const chat: Message[] = [
      { role: "system", content: SYSTEM_PROMPT },
      ...userTranscriptArr,
    ];
    const response = await toolSelectionModel.generate(chat, TOOL_DEFINITIONS);
    console.log(response, "LLM response");
    const parsedToolResponse = parseToolCalls(response);
    console.log(parsedToolResponse, "Parsed LLM response");
    const invocation = toolInvoker(parsedToolResponse);
    console.log(invocation, "tool invocation response");
    return {
      role: "assistant",
      content: `Tool: ${invocation.status}, Message: ${invocation?.message ?? "No Message"} `,
    };
  } catch (error) {
    return {
      role: "assistant",
      content: error instanceof Error ? error.message : String(error),
    };
  }
}
