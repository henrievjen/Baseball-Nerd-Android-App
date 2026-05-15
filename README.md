# Baseball Nerd — Android App

A companion app for MLB fans, featuring live scores, standings, player stats, and interactive games.

## Features
- **Live Scores**: Real-time updates with line scores, count, and runners.
- **Game Details**: In-depth look at each game, including play-by-play, box scores, and lineups.
- **Standings**: Current MLB standings by division and wild card.
- **Player Stats**: Detailed season statistics and splits for every player.
- **Who's Hot**: Identify top performers across the league.
- **Interactive Games**:
  - **Bingo**: Play along with the game and mark events on your board.
  - **Play Predictor**: Predict the outcome of the current at-bat.
- **Android Auto**: Access scores and game details on the go.

## Tech Stack
- **Kotlin**: Modern Android development.
- **MVVM Architecture**: Clean separation of concerns.
- **Retrofit**: Networking with MLB Data API.
- **Coil**: Image loading, including SVG support for team logos.
- **Navigation Component**: Simplified app navigation.
- **View Binding**: Type-safe view access.
- **Coroutines & Flow**: Asynchronous programming and data streams.

## Project Structure
```text
app/src/main/
├── java/com/mlbcompanion/
│   ├── auto/         # Android Auto screens
│   ├── data/         # Repositories and models
│   ├── ui/           # Fragments and ViewModels
│   └── util/         # Helper classes
└── res/              # Layouts, drawables, and resources
```

## Setup
1. Clone the repository.
2. Open in Android Studio.
3. Build and run on an emulator or physical device.
