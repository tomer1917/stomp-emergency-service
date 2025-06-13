#include "../include/ConnectionHandler.h"
#include <iostream>
#include <thread>
#include <vector>
#include <string>
#include <mutex>
#include <condition_variable>
#include <fstream>
#include <unordered_map>
#include <queue>
#include "../src/event.cpp"
#include <sys/stat.h>


using namespace std;




class CommandQueue {
public:
    // Push a command string into the queue
    void push(const string& command) {
        lock_guard<mutex> lock(mtx);
        q.push(command);
        cv.notify_one();  // Notify the communication thread
    }

    // Pop a command string from the queue
    bool pop(string& command) {
        unique_lock<mutex> lock(mtx);
        cv.wait(lock, [this]() { return !q.empty(); });  // Wait until the queue has data

        command = q.front();
        q.pop();
        return true;
    }
    // Check if the queue is empty
    bool isEmpty() {
        lock_guard<mutex> lock(mtx); // Lock the mutex for thread safety
        return q.empty();
    }

private:
    queue<string> q;                // The shared queue of commands
    mutex mtx;                       // Mutex for mutual exclusion
    condition_variable cv;           // Condition variable for synchronization
};

// Global variables
CommandQueue commandQueue;
bool done = false; // Flag to signal threads to terminate
short port = 0;
string host = "stomp.cs.bgu.ac.il";
string username = "";
ConnectionHandler *handler = nullptr;
int subscriptionId = 0;
int receiptId = 0;
unordered_map<string, int> channelSubscriptions;
std::map<std::string, std::map<std::string, std::vector<std::string>>> summeryMap; // Channel -> User -> Messages



// Helper function to check if a file exists
bool fileExists(const string &filePath) {
    struct stat buffer;
    return (stat(filePath.c_str(), &buffer) == 0);
}

// Function to split a string by a delimiter
vector<string> split(const string &str, char delimiter)
{
    vector<string> tokens;
    string token;
    istringstream tokenStream(str);
    while (getline(tokenStream, token, delimiter))
    {
        tokens.push_back(token);
    }
    return tokens;
}


// Function to extract receiptId from the server response
int extractReceiptIdFromResponse(const string &response)
{
    size_t pos = response.find("receipt-id:");
    if (pos != string::npos)
    {
        size_t endPos = response.find("\n", pos);
        if (endPos != string::npos)
        {
            string receiptIdStr = response.substr(pos + 11, endPos - pos - 11);//11 is the length of "receipt-id:"
            return stoi(receiptIdStr);
        }
    }
    return -1; // Return -1 if receipt-id is not found
}

// Function to get the channel name by its id
string getChannelNameById(int id) {
    for (const auto& pair : channelSubscriptions) {
        if (pair.second == id) {
            return pair.first;
        }
    }
    return ""; // Return empty string if id is not found
}

string extractDestination(const string& frame) {
    vector<string> lines = split(frame, '\n');
    for (const string& line : lines) {
        if (line.find("destination:") != string::npos) {
            string destination = line.substr(line.find(":") + 1);
            if (!destination.empty() && destination[0] == '/') {
                destination = destination.substr(1);  // Remove the leading slash
            }
            return destination;
        }
    }
    return "";  // Return empty string if destination is not found
}

string extractId(const string& frame) {
    vector<string> lines = split(frame, '\n');
    for (const string& line : lines) {
        if (line.find("id:") != string::npos) {
            return line.substr(line.find(":") + 1);
        }
    }
    return "";  // Return empty string if id is not found
}

//extract the message details from the input
void extractMessageDetails(const string& input) {
    string username, channel, message;
    stringstream ss(input);
    string line;

    // Extracting username, channel, and message
    while (getline(ss, line)) {
        if (line.find("user:") != string::npos) {
            // Extract username after "user:"
            username = line.substr(line.find(":") + 1);  
        }
        if (line.find("destination:") != string::npos) {
            // Extract channel name after "destination:", remove the slash
            channel = line.substr(line.find(":") + 2);  
            if (channel[0] == '/') {
                channel = channel.substr(1);  // Remove the slash
            }
        }
        if (line.find("city:") != string::npos) {
            // Extract everything from "city:" onward as the message
            size_t pos = line.find("city:") + 5;  // Find where "city:" starts
            message = line.substr(pos);
            // Read the rest of the lines for the full message
            while (getline(ss, line)) {
                message += "\n" + line;
            }
            break;  // Assuming the message ends after all lines following "city:"
        }
    }
    // Update the summary map with the extracted details
    //summeryMap[channel][username] = message;
    summeryMap[channel][username].push_back(message);

}


// Split the input string by "MESSAGE" and return the last segment
string getLastMessage(const string& input) {
    vector<string> parts;
    size_t start = 0, end = 0;

    while ((end = input.find("MESSAGE", start)) != string::npos) {
        if (end > start) {
            parts.push_back(input.substr(start, end - start));
        }
        start = end + 7; // Skip "MESSAGE"
    }

    if (start < input.length()) {
        parts.push_back(input.substr(start)); // Add the last segment
    }

    // Return the last non-empty part, re-adding "MESSAGE" to maintain context
    if (!parts.empty()) {
        return "MESSAGE" + parts.back();
    }

    return ""; // Return an empty string if no valid segments were found
}

// Command methods
string loginCommand(const vector<string> &words)
{
    if (words.size() < 4)
    {
        return"PROBLEM\nMissing username, password or port." ;
    }

    // Extract the IP address and port
    string hostPort = words[1];
    size_t colonPos = hostPort.find(":");
    if (colonPos == string::npos)
    {
        return"PROBLEM\nInvalid format for host and port.";
    }

    string ipAddress = hostPort.substr(0, colonPos);
    string portStr = hostPort.substr(colonPos + 1);

    port = atoi(portStr.c_str());
    host = ipAddress;

    // Initialize ConnectionHandler
    handler = new ConnectionHandler(host, port);
    if (!handler->connect())
    {
        delete handler;
        handler = nullptr;
        return"PROBLEM\nCould not connect to server.";
    }

    // Save the username
    username = words[2];


    // Create and send a CONNECT frame
    string connectFrame = "CONNECT\naccept-version:1.2\nhost:stomp.cs.bgu.ac.il\nlogin:" + words[2] + "\npasscode:" + words[3] +"\nreceipt:"+ to_string(receiptId)+ "\n\n\0";
    receiptId++;
    return connectFrame;
}

string joinCommand(const vector<string> &words)
{
    if (words.size() < 2)
    {
        return "PROBLEM\nMissing channel name.";
    }
    string channelName = words[1];
    if (channelSubscriptions.find(channelName) != channelSubscriptions.end())
    {
        return "PROBLEM\nalready joined channel" ;
    }
    
    //according to the given client, you should not add / to the channel name in the SUBSCRIBE frame
    string subscribeFrame = "SUBSCRIBE\ndestination:" + channelName + "\nid:" + to_string(subscriptionId) + "\nreceipt:" + to_string(receiptId) + "\n\n\0";
    subscriptionId++;
    receiptId++;
    return subscribeFrame;
}

string exitCommand(const vector<string> &words)
{
    if (words.size() < 2)
    {
        return"PROBLEM\nMissing channel name.";
    }

    string channelName = words[1];
    //check if the channel is subscribed
    if (channelSubscriptions.find(channelName) == channelSubscriptions.end())
    {
        return "PROBLEM\nYou are not subscribed to " + channelName ;
    }

    string unsubscribeFrame = "UNSUBSCRIBE\nid:" + to_string(channelSubscriptions[channelName]) + "\nreceipt:" + to_string(receiptId) + "\n\n\0";
    receiptId++;
    return unsubscribeFrame;
}

string reportCommand(const vector<string> &words)
{
    if (words.size() < 2)
    {
        return "PROBLEM\nMissing argiments (file)." ;
    }
    string filePath = words[1];
    // parse the JSON file and retrieve the events using the Event class and the parseEventsFile function
    names_and_events parsedData = parseEventsFile(filePath);
    for (Event event : parsedData.events) {
        event.setEventOwnerUser(username);
        //according to the given client, you should add / to the channel name in the SEND frame
        string eventFrame = "SEND\ndestination:/" + parsedData.channel_name + "\nreceipt:"+to_string(receiptId)+"\n\n";
        eventFrame += "user:" + event.getEventOwnerUser() + "\n";
        eventFrame += "city:" + event.get_city() + "\n";
        eventFrame += "event name:" + event.get_name() + "\n";
        eventFrame += "date time:" + to_string(event.get_date_time()) + "\n";

        //add general information to the frame
        if (event.get_general_information().size() > 0)
        {
            eventFrame += "general information:\n";
            for (const auto& pair : event.get_general_information()) {
                eventFrame += "    " + pair.first + ":" + pair.second + "\n";
            }
        }
        eventFrame += "description:\n" + event.get_description() + "\n";
        eventFrame += "\0";
        commandQueue.push(eventFrame);
    }
    string eventFrame = "SEND\ndestination:/" + parsedData.channel_name + "\nreceipt:"+to_string(receiptId)+"\n\n";
    eventFrame += "TERMINATE1111\n";
    commandQueue.push(eventFrame);
    
    if (parsedData.events.size() == 0)
    {
        cout << "nothing to report" << endl;
    }
    else
    {
        cout << "reported" << endl;
    }
    return "";
}

string summaryCommand(const vector<string> &words)
{
    if (words.size() < 3)
    {
        return "PROBLEM\nMissing argiments (channel,user, file)." ;
    }
    string channelName = words[1];
    string userName = words[2];
    string filePath = words[3];
    //check if the channel is subscribed
    if (channelSubscriptions.find(channelName) == channelSubscriptions.end())
    {
        return "PROBLEM\nYou are not subscribed to " + channelName ;
    }
    if(summeryMap.find(channelName) == summeryMap.end() || summeryMap[channelName].find(userName) == summeryMap[channelName].end())
    {
        return "PROBLEM\nNo messages from " + userName + " in " + channelName ;
    }
    std::vector<std::string> messages = summeryMap[channelName][userName];
    int amountOfMessages = messages.size();
    int activeEvents = 0;
    int forcesArrivalAtScene = 0;
    for (const string& message : messages) {
        vector<string> lines = split(message, '\n');
        for (const string& line : lines) {
            if (line.find("active") != string::npos && line.find("true") != string::npos) {
                activeEvents++;
            }
            if (line.find("forces_arrival_at_scene") != string::npos && line.find("true") != string::npos) {
                forcesArrivalAtScene++;
            }
        }
    }

    string result = "Channel: " + channelName + "\nStats:\nTotal:"+ to_string(amountOfMessages) + "\nactive: " + to_string(activeEvents) 
    + "\nforces arrival at scene:" + to_string(forcesArrivalAtScene) 
    + "\n\nEvent Reports:\n";

    
    //add the reports to the result
    for(int i = 0; i < amountOfMessages; i++)
    {
        //change and add reports to this format: \nDD/MM/YYYY HH:MM:SS - event name - city:\ndecription" 
        vector<string> lines = split(messages[i], '\n');
        string date = "";
        string eventName = "";
        string city = "";
        int counter = 0;
        for(const string& line : lines)
        {
            
            vector<string> words = split(line, ':');
            if(words.size()<1)
            {
                continue;
            }
            if(words[0] == "event name")
            {
                eventName = words[1];
            }
            else if(words[0] == "city"|| counter == 0)
            {
                if(words[0] == "city")
                {
                    city = words[1];
                }
                else
                {
                    city = words[0];
                }
            }
            else if(words[0] == "date time")
            {
                date = words[1];
            }
            counter++;
        }
        // Convert the date time number to a readable format
        time_t rawtime = stoi(date);
        struct tm * dt;
        char buffer[30];
        dt = localtime(&rawtime);
        strftime(buffer, sizeof(buffer), "%d/%m/%Y %H:%M:%S", dt);
        string formattedDate(buffer);

        result += formattedDate + " - " + eventName + " - " + city + ":\n" + lines.back() + "\n\n";

    }
    //check if there is a txt file in filePath. if it exist - write to it, else create a txt file with this name in filePath
    
    if (fileExists(filePath))
    {
        //write the result to the file
        ofstream outFile(filePath);
        if (outFile.is_open()) {
            outFile << result;
            outFile.close();
        } else {
            return "PROBLEM\nCould not open file " + filePath;
        } 
    }
    else
    {
        ofstream outFile;
        outFile.open(filePath);
        if (!outFile.is_open()) 
            return "PROBLEM\nCould not open file " + filePath;
        outFile << result;
        outFile.close();

    }
    
    

    cout << "Summary saved to " << filePath << endl;
    return"";

}

string logoutCommand(const vector<string> &words)
{
    string disconnectFrame = "DISCONNECT\nreceipt:"+ to_string(receiptId)+"\n\n\0";
    receiptId++;
    return disconnectFrame;
    
}

// Main function to process the commands
string createCommand(string line)
{
    vector<string> words = split(line, ' ');

    if (words.empty())
    {
         return "PROBLEM\nNo command entered.";
    }

    string command = words[0];

    if (command == "login")
    {
        return loginCommand(words);
    }
    else
    {
        if (username.empty())
        {
            return "PROBLEM\nYou must login first.";
        }
        else if (command == "join")
        {
            return joinCommand(words);
        }
        else if (command == "exit")
        {
            return exitCommand(words);
        }
        else if (command == "report")
        {
            return reportCommand(words);
        }
        else if (command == "summary")
        {
            return summaryCommand(words);
        }
        else if (command == "logout")
        {
            return logoutCommand(words);
        }
        else
        {
            return "PROBLEM\nUnknown command.";
        }
    }
}

void handleError(const string& error) {
    //print the error and close all the threads 
    cout << "error received from server:\n"<<error << endl;
    done = true;
    commandQueue.push("END");

}

void handleResponse(const string& command, const string& response) {
    // Split the response by '\n' to parse the instruction
    vector<string> commandLines = split(command, '\n');
    
    // Get the instruction (first line)
    string instruction = commandLines.empty() ? "" : commandLines[0];
    // Handle different instructions with if-else
    if (instruction == "CONNECT") {
        //if the response first word is CONNECTED print "Login successful", else print the error
        vector<string> responseLines = split(response, '\n');
        if (responseLines[0] == "CONNECTED") {
            cout << "Login successful" << endl;
        } else {
            handleError(response);
        }        
    }
    else if (instruction == "SUBSCRIBE") {
        vector<string> responseLines = split(response, '\n');
        if (responseLines[0] == "RECEIPT") {
            int SubId = stoi(extractId(command));
            //save the channel the user subscribed to with the subscription id
            channelSubscriptions[extractDestination(command)] = SubId;
            cout << "Joined channel "<< getChannelNameById(SubId) << endl;
        } else {
            handleError(response);
        }  
    }
    else if (instruction == "UNSUBSCRIBE") {
        vector<string> responseLines = split(response, '\n');
        if (responseLines[0] == "RECEIPT") {
            int subId = stoi(extractId(command));
            string channelName = getChannelNameById(subId);
            channelSubscriptions.erase(channelName);
            cout << "Exited channel "<< channelName << endl;
        } else {
            handleError(response);
        }
    }
    else if (instruction == "SEND") {
        vector<string> responseLines = split(response, '\n');
        if (responseLines[0] == "MESSAGE") {
            //save the message the user sent to the channel
            extractMessageDetails(response);
        } else {
            handleError(response);
        }
    }
    else if (instruction == "DISCONNECT") {
        vector<string> responseLines = split(response, '\n');
        if (responseLines[0] == "RECEIPT") {
            cout << "Logout successful" << endl;
            handler->close();
            delete handler;
            handler = nullptr;
            username = "";
            summeryMap.clear();
            channelSubscriptions.clear();
            
        } else {
            handleError(response);
        }
    }
    else {
        cout << "warning: you sent unrecognized frame to server "<< command << endl;
    }
}

void handleSendCommand(string &response, const string &command) {
    while (true) {
        if (!handler->getLine(response)) {
            cout << "Error: Failed to receive response from server." << endl;
            break;
        }

        vector<string> lines = split(response, '\n');
        // Check for MESSAGE, RECEIPT, or ERROR frames
        bool receiptFound = false;
        for (const string& line : lines) {
            if (line == "RECEIPT") {
                receiptFound = true;
                    break;
            }
            if(line == "ERROR") {
                cout << "Error frame received: " << response << endl;
                break;
            }
        }
        if (receiptFound)
        {
            cout << "RECEIPT received, exiting message handling loop." << endl;
            break;
        }
        else{
            cout << "\n************Server response:\n" << response << endl;
            if(response.find("TERMINATE1111") != string::npos)
            {
                cout << "TERMINATE1111 received, exiting message handling loop." << endl;
                break;
            }
            handleResponse(command, response);
        }
    }
}

void handleMessagesFromServer(){
    bool reset = false;
    string response;
        while (!reset) {
        reset = !handler->checkBuffer();
        if (!handler->getLine(response)) {
            cout << "Error: Failed to receive response from server." << endl;
            break;
        }
        vector<string> lines = split(response, '\n');
        // Check for MESSAGE, RECEIPT, or ERROR frames
        bool receiptFound = false;
        for (const string& line : lines) {
            if (line == "RECEIPT") {
                receiptFound = true;
                    break;
            }
            if(line == "ERROR") {
                cout << "Error frame received: " << response << endl;
                break;
            }
        }
        if (receiptFound)
        {
            cout << "RECEIPT received, exiting message handling loop." << endl;
            break;
        }
        else{
            response = getLastMessage(response);
            cout << "\n************Server response:\n" << response << endl;

            //check if the response contains TERMINATE1111
            if(response.find("TERMINATE1111") != string::npos)
            {
                cout << "TERMINATE1111 received, exiting message handling loop." << endl;
                break;
            }
            extractMessageDetails(response);
        }
    }
}

void handleNormalCommand(string &response, const string &command) {
    if (handler->getLine(response)) {
        cout << "Server response:\n" << response << endl;
        handleResponse(command, response);
    } else {
        cout << "Error: Failed to receive response from server." << endl;
    }
}


// Threads for handling user input and communication
void keyboardThread() {
    string input;
    while (!done) {
        // Get a line of input from the user
        getline(cin, input);

        // Exit condition for the keyboard thread
        if (input == "end") {
            commandQueue.push("END");
            done = true;
            break;
        }

        // Create a STOMP frame and push it to the queue
        string stompFrame = createCommand(input);

        vector<string> commandLines = split(stompFrame, '\n');
        string instruction = commandLines.empty() ? "" : commandLines[0];
        // If the instruction is "PROBLEM", do nothing
        if (instruction == "PROBLEM") {
            cout << commandLines[1] << endl;
            continue;
        }else if(stompFrame == ""){
            continue;
        }
        else{
            commandQueue.push(stompFrame);
        }
    }
}

void communicationThread() {
    while (!done) {
        if(handler == nullptr)
        {
            continue;
        }


        // Check for incoming messages from the server
        string message;
        if (handler->checkBuffer()) {
            handleMessagesFromServer();
        }

        // Check for outgoing commands from the user
        string command;
        if (commandQueue.isEmpty())
        {
            continue;
        }
        if (commandQueue.pop(command)) {
            if (command == "END") {
                break;
            }

            // Send the command to the server
            if (handler->sendLine(command)) {
                string response;
                string commandType = command.substr(0, command.find("\n"));

                if (commandType == "SEND") {
                    // Handle SEND command with multiple MESSAGE frames
                    handleSendCommand(response, command);
                } else {
                    cout << "Sending command:\n" << command << endl;
                    // Handle non-SEND commands normally
                    handleNormalCommand(response, command);
                }
            } else {
                cout << "Error: Failed to send command." << endl;
            }
        }
    }
}


int main(int argc, char *argv[])
{
    cout << "Start typing (type 'end' to stop):" << endl;

    // Launch the threads
    thread kbThread(keyboardThread);
    thread commThread(communicationThread);

    // Wait for the threads to finish
    kbThread.join();
    commThread.join();

    // Cleanup
    if (handler)
    {
        delete handler;
        handler = nullptr;
    }

    cout << "Client shut down." << endl;
    return 0;
}
