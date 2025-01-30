import { BackgroundLocation } from 'elsapiens-background-location';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    BackgroundLocation.echo({ value: inputValue })
}
