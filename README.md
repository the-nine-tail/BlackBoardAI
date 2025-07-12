# BlackBoardAI - Clean Architecture Android App

## Overview
BlackBoardAI is an Android application built using **MVVM Clean Architecture** principles, featuring local storage with Room database, dependency injection with Hilt, and AI integration using Google AI Edge SDK with Gemma 3n model.

## Architecture

### Clean Architecture Layers

```
app/
├── domain/                    # Domain Layer (Business Logic)
│   ├── entity/               # Domain entities
│   ├── repository/           # Repository interfaces
│   └── usecase/              # Use cases (business logic)
├── data/                     # Data Layer (Data Sources)
│   ├── local/               # Local data sources
│   │   ├── dao/             # Room DAOs
│   │   ├── database/        # Room database
│   │   └── entity/          # Room entities
│   ├── ai/                  # AI service implementation
│   └── repository/          # Repository implementations
├── presentation/            # Presentation Layer (UI)
│   ├── ui/                  # Compose UI components
│   └── viewmodel/           # ViewModels
└── di/                      # Dependency Injection modules
```

## Features Implemented

### 1. **Room Database Integration**
- ✅ Room database setup with entities, DAOs, and database class
- ✅ Local storage for chat messages
- ✅ CRUD operations for chat history
- ✅ Flow-based reactive data access

### 2. **Hilt Dependency Injection**
- ✅ Hilt modules for database, repositories, and services
- ✅ Application class with `@HiltAndroidApp`
- ✅ Activities and ViewModels with Hilt annotations
- ✅ Proper dependency graph setup

### 3. **Google AI Edge SDK Setup**
- ✅ AI service interface and implementation
- ✅ Placeholder for Gemma 3n model integration
- ✅ Reactive AI response handling with Flow
- ⚠️ **TODO**: Actual Google AI Edge SDK implementation

### 4. **MVVM Clean Architecture**
- ✅ Separation of concerns across layers
- ✅ Domain-driven design with entities and use cases
- ✅ Repository pattern for data access abstraction
- ✅ Reactive programming with Kotlin Flow

### 5. **Modern Android UI**
- ✅ Jetpack Compose UI
- ✅ Material 3 design system
- ✅ Chat interface with message bubbles
- ✅ Real-time message updates

## Key Components

### Domain Layer
- **`ChatMessage`**: Core domain entity representing chat messages
- **`ChatRepository`**: Interface for chat data operations
- **`AIRepository`**: Interface for AI service operations
- **`SendMessageUseCase`**: Business logic for sending messages
- **`GetMessagesUseCase`**: Business logic for retrieving messages

### Data Layer
- **`ChatMessageEntity`**: Room database entity
- **`ChatMessageDao`**: Room DAO for database operations
- **`BlackBoardAIDatabase`**: Room database class
- **`GoogleAIService`**: AI service implementation (placeholder)
- **`ChatRepositoryImpl`**: Repository implementation using Room
- **`AIRepositoryImpl`**: Repository implementation using AI service

### Presentation Layer
- **`ChatViewModel`**: ViewModel managing chat state
- **`ChatScreen`**: Main chat UI composable
- **`ChatMessageItem`**: Individual message UI component

### Dependency Injection
- **`DatabaseModule`**: Provides database-related dependencies
- **`RepositoryModule`**: Binds repository interfaces to implementations
- **`BlackBoardAIApplication`**: Application class with Hilt setup

## Dependencies Added

```kotlin
// Hilt Dependency Injection
implementation("com.google.dagger:hilt-android:2.48")
kapt("com.google.dagger:hilt-compiler:2.48")

// Room Database
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")

// Google AI Edge SDK
implementation("com.google.ai.edge.litert:litert-android:1.0.1")
implementation("com.google.ai.edge.litert:litert-support-api:1.0.1")

// Navigation & Compose
implementation("androidx.navigation:navigation-compose:2.7.6")
implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
```

## Setup Instructions

1. **Sync Project**: Ensure all dependencies are downloaded
2. **Build Project**: Run `./gradlew build` to compile
3. **Run App**: Deploy to device or emulator

## TODO: Google AI Edge SDK Integration

The current implementation includes a placeholder for the Google AI Edge SDK. To complete the integration:

1. **Download Gemma 3n Model**: Add model files to `assets/` folder
2. **Initialize Model**: Implement actual model loading in `GoogleAIService`
3. **Inference**: Replace placeholder response generation with actual AI inference
4. **Error Handling**: Add proper error handling for model operations

```kotlin
// Example of actual implementation needed in GoogleAIService
private lateinit var interpreter: Interpreter

suspend fun initializeModel(): Boolean {
    return try {
        // Load model from assets
        val modelBuffer = loadModelFromAssets("gemma_3n_model.tflite")
        interpreter = Interpreter(modelBuffer)
        isModelInitialized = true
        true
    } catch (e: Exception) {
        false
    }
}
```

## Testing

The project includes comprehensive testing setup:
- **Unit Tests**: Domain layer logic testing
- **Integration Tests**: Repository implementations
- **UI Tests**: Compose UI testing
- **Hilt Testing**: Dependency injection testing

## Build Configuration

- **Compile SDK**: 34 (Android 14)
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34
- **Kotlin**: 1.9.0
- **Compose**: 2023.10.01

## Contributing

1. Follow Clean Architecture principles
2. Use MVVM pattern for UI layer
3. Implement proper error handling
4. Add unit tests for new features
5. Follow Material 3 design guidelines

## License

This project is open source and available under the [MIT License](LICENSE). 