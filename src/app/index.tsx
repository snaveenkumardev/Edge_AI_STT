import { useRouter } from "expo-router";
import { Button, StyleSheet, Text, View } from "react-native";

export default function Index() {
  const router = useRouter();

  return (
    <View style={styles.container}>
      <Text>Edit src/app/index.tsx to edit</Text>
      <Button
        title="Harm Detector"
        onPress={() => router.push("/harm-detector")}
      />
       <Button
        title="Voice Detector"
        onPress={() => router.push("/voice_detector")}
      />
       <Button
        title="Live audio transcribe"
        onPress={() => router.push("/live_voice_transcripe")}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
  },
});
