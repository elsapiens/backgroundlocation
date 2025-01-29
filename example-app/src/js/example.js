import { BackgroudLocation } from 'elsapiens-background-location';

window.testEcho = () => {
    const inputValue = document.getElementById("echoInput").value;
    BackgroudLocation.echo({ value: inputValue })
}
