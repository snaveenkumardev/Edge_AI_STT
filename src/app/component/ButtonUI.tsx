import { StyleSheet, Text, TouchableOpacity } from 'react-native'

interface ButtonUI_Type {
    onPress: () => void
}

const ButtonUI = ({ onPress }: ButtonUI_Type) => {
    return (
        <TouchableOpacity style={styles.safetyModeContainer} onPress={onPress}>
            <Text style={styles.safetyModeBtnLabel}>Safety Mode</Text>
            <Text style={[styles.safetyModeBtnLabel, { marginTop: 10 }]}>ON</Text>
        </TouchableOpacity>
    )
}

export default ButtonUI

const styles = StyleSheet.create({
    safetyModeContainer: {
        width: 200,
        height: 200,
        borderRadius: 200,
        backgroundColor: 'green',
        justifyContent: 'center',
        alignItems: 'center'
    },
    safetyModeBtnLabel: {
        fontSize: 25,
        color: 'white'
    }
})