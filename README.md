# RedZappay - Mobile Payment App

## Overview

RedZappay is a mobile payment application designed for Android that facilitates transactions. It provides users with a platform to manage their profiles, generate QR codes, and view their transaction histories. The app uses Firebase for user authentication and data storage.

## Features

* **User Profile Management:** Users can create and edit their profiles, including details like name, date of birth, mobile number, email, and UPI IDs.
* **QR Code Generation:** The app enables users to generate QR codes.
* **Transaction History:** Users can view their transaction history, with options to filter by date.
* **Firebase Integration:**
    * Authentication: User authentication is handled using Firebase Auth.
    * Firestore: User data and transaction details are stored in Firebase Firestore.

## Technical Architecture

* Language: Java
* Database: Firebase Firestore
* Authentication: Firebase Authentication
* UI Components: Android UI elements (EditText, Button, TextView, RecyclerView)

## Code Structure

The project comprises the following key activities:

* MainActivity: Splash screen activity that redirects the user to the Login Activity.
* LoginActivity: Handles user login functionality using Firebase Authentication.
* SignupActivity: Allows new users to create an account, storing their details in Firestore.
* HomeActivity: Provides the main navigation, allowing users to access different features.
* ProfileActivity: Displays the user's profile information retrieved from Firestore.
* EditProfileActivity: Enables users to modify their profile data in Firestore.
* QRGeneratorActivity: Generates QR codes.
* TransactionHistoryActivity: Displays the user's transaction history, fetched from Firestore, with date filtering.
* TransactionAdapter: Adapts transaction data for display in a RecyclerView.
* Transaction: Represents the data structure for a single transaction.

## Setup Instructions

1.  **Clone the Repository:** Clone the GitHub repository to your local machine.
2.  **Set up Firebase:**
    * Create a project in the Firebase Console.
    * Enable Authentication (Email/Password).
    * Create a Firestore database.
    * Download the `google-services.json` file and place it in the `app/` directory.
3.  **Configure the Project:**
    * Open the project in Android Studio.
    * Ensure that the `google-services` plugin is correctly configured in your project's `build.gradle` and the app's `build.gradle` files.
4.  **Run the Application:** Build and run the application on an Android emulator or device.

## Libraries Used

* Firebase Authentication
* Firebase Firestore
* Android RecyclerView
* Android Calendar

## Future Enhancements

* Implement additional payment gateway integrations.
* Add more detailed transaction information.
* Implement user interface improvements.
* Add unit and UI tests.

## Contribution Guidelines

Contributions are welcome.


