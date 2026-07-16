import { ScrollView, StyleSheet, Text, View } from 'react-native'
import { Message } from 'react-native-executorch'

interface ChatUI_Type {
    llmConversations: Message[]
}

const ChatUI = ({ llmConversations }: ChatUI_Type) => {
    return (
        <ScrollView contentContainerStyle={styles.scrollContent} style= {{width: '100%'}}>
            {llmConversations.map((message, index) => (
                <View
                    key={index}
                    style={[
                        styles.bubble,
                        message.role === 'assistant' ? styles.assistantBubble : styles.userBubble,
                    ]}
                >
                    <Text style={styles.bubbleText}>{message.content}</Text>
                </View>
            ))}
        </ScrollView>
    )
}

export default ChatUI

const styles = StyleSheet.create({
    scrollContent: {
        paddingVertical: 10,
    },
    bubble: {
        borderRadius: 12,
        paddingVertical: 8,
        paddingHorizontal: 12,
        marginVertical: 6,
        backgroundColor: '#e5e5ea',
    },
    assistantBubble: {
        alignSelf: 'flex-start',
    },
    userBubble: {
        alignSelf: 'flex-end',
        backgroundColor: "rgba(0, 255, 0, 0.3)"
    },
    bubbleText: {
        fontSize: 15,
        color: '#000',
    },
})
