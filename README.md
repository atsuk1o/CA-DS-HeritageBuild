HeritageBuild (SDG 11 - Sustainable Cities)

What is HeritageBuild?
It is a distrbuted system designed to help protect world heritage sites and promot sustainable construction in developing regions. This project addresses UN Sustainable Development Goal 11 ( https://sdgs.un.org/goals/goal11 ) by focusing on three core needs:
1. Monitoring risks to heritage sites.
2. Sourcing sustainable local building materials.
3. Coordinating frants and tecnhical aid.

Folder Structure
I've organized this project into modules to keep code clean and follow gRPC best practices:

client-gui/ - where the ClientApp lives. This will host the GUI that allows users to interact with all three services.

naming-service/ - 'phonebook' of my system. Services register here so the client can find them automatically without hardcoded IPs

protos/ - 'contact' for the whole system. All me .proto files live here so the client and the services are always in sync

scripts/ - holds automation script run-all.sh to get whole ds up and running with one command.

services/ - contains the three main microservices: heritage-monitor, material-vault and grant-coordinator. Each one runs independently.

How it works?
System is entirely built in Java and uses gRPC for communication.
1.Discovery: Services register with the Naming Service on startup.
2.Interaction: A user might report a risk via the Monitor service, which then triggers a need for funding via the Grant service.
3.Efficiency: I use various gRPC styles (Unary and Streaming) to ensure data like material lists or risk alerts are sent efficiently.