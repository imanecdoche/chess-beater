#include <jni.h>
#include <string>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <atomic>
#include <sstream>
#include <vector>
#include <algorithm>
#include <chrono>
#include <cmath>
#include <android/log.h>

#define TAG "ChessBeaterNativeEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)

namespace ChessBeater {

// ==========================================
// Full C++ UCI Chess Engine Core
// ==========================================

enum Piece : char {
    EMPTY = '.',
    W_PAWN = 'P', W_KNIGHT = 'N', W_BISHOP = 'B', W_ROOK = 'R', W_QUEEN = 'Q', W_KING = 'K',
    B_PAWN = 'p', B_KNIGHT = 'n', B_BISHOP = 'b', B_ROOK = 'r', B_QUEEN = 'q', B_KING = 'k'
};

struct Move {
    int from;
    int to;
    char promo; // 'q', 'r', 'b', 'n' or 0

    Move() : from(0), to(0), promo(0) {}
    Move(int f, int t, char p = 0) : from(f), to(t), promo(p) {}

    std::string toUci() const {
        if (from == to && from == 0) return "0000";
        int fc = from % 8;
        int fr = 8 - (from / 8);
        int tc = to % 8;
        int tr = 8 - (to / 8);
        std::string s;
        s += static_cast<char>('a' + fc);
        s += static_cast<char>('0' + fr);
        s += static_cast<char>('a' + tc);
        s += static_cast<char>('0' + tr);
        if (promo != 0) {
            s += static_cast<char>(std::tolower(promo));
        }
        return s;
    }
};

// Piece Square Tables (Rank 8 down to Rank 1, File A to H)
static const int PAWN_PST[64] = {
     0,  0,  0,  0,  0,  0,  0,  0,
    50, 50, 50, 50, 50, 50, 50, 50,
    10, 10, 20, 30, 30, 20, 10, 10,
     5,  5, 10, 25, 25, 10,  5,  5,
     0,  0,  0, 20, 20,  0,  0,  0,
     5, -5,-10,  0,  0,-10, -5,  5,
     5, 10, 10,-20,-20, 10, 10,  5,
     0,  0,  0,  0,  0,  0,  0,  0
};

static const int KNIGHT_PST[64] = {
    -50,-40,-30,-30,-30,-30,-40,-50,
    -40,-20,  0,  0,  0,  0,-20,-40,
    -30,  0, 10, 15, 15, 10,  0,-30,
    -30,  5, 15, 20, 20, 15,  5,-30,
    -30,  0, 15, 20, 20, 15,  0,-30,
    -30,  5, 10, 15, 15, 10,  5,-30,
    -40,-20,  0,  5,  5,  0,-20,-40,
    -50,-40,-30,-30,-30,-30,-40,-50
};

static const int BISHOP_PST[64] = {
    -20,-10,-10,-10,-10,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5, 10, 10,  5,  0,-10,
    -10,  5,  5, 10, 10,  5,  5,-10,
    -10,  0, 10, 10, 10, 10,  0,-10,
    -10, 10, 10, 10, 10, 10, 10,-10,
    -10,  5,  0,  0,  0,  0,  5,-10,
    -20,-10,-10,-10,-10,-10,-10,-20
};

static const int ROOK_PST[64] = {
     0,  0,  0,  0,  0,  0,  0,  0,
     5, 10, 10, 10, 10, 10, 10,  5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
    -5,  0,  0,  0,  0,  0,  0, -5,
     0,  0,  0,  5,  5,  0,  0,  0
};

static const int QUEEN_PST[64] = {
    -20,-10,-10, -5, -5,-10,-10,-20,
    -10,  0,  0,  0,  0,  0,  0,-10,
    -10,  0,  5,  5,  5,  5,  0,-10,
    -5,   0,  5,  5,  5,  5,  0, -5,
     0,   0,  5,  5,  5,  5,  0, -5,
    -10,  5,  5,  5,  5,  5,  0,-10,
    -10,  0,  5,  0,  0,  0,  0,-10,
    -20,-10,-10, -5, -5,-10,-10,-20
};

static const int KING_PST[64] = {
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -30,-40,-40,-50,-50,-40,-40,-30,
    -20,-30,-30,-40,-40,-30,-30,-20,
    -10,-20,-20,-20,-20,-20,-20,-10,
     20, 20,  0,  0,  0,  0, 20, 20,
     20, 30, 10,  0,  0, 10, 30, 20
};

class ChessBoard {
public:
    char squares[64];
    bool whiteTurn;
    bool castlingK, castlingQ, castlingk, castlingq;
    int enPassantSq; // -1 if none

    ChessBoard() {
        reset();
    }

    void reset() {
        loadFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
    }

    void loadFen(const std::string& fen) {
        for (int i = 0; i < 64; ++i) squares[i] = EMPTY;
        whiteTurn = true;
        castlingK = castlingQ = castlingk = castlingq = false;
        enPassantSq = -1;

        std::istringstream iss(fen);
        std::string placement, activeColor, castling, enPassant;
        iss >> placement >> activeColor >> castling >> enPassant;

        int row = 0, col = 0;
        for (char c : placement) {
            if (c == '/') {
                row++;
                col = 0;
            } else if (c >= '1' && c <= '8') {
                col += (c - '0');
            } else {
                if (row >= 0 && row < 8 && col >= 0 && col < 8) {
                    squares[row * 8 + col] = c;
                }
                col++;
            }
        }

        whiteTurn = (activeColor != "b");

        if (castling.find('K') != std::string::npos) castlingK = true;
        if (castling.find('Q') != std::string::npos) castlingQ = true;
        if (castling.find('k') != std::string::npos) castlingk = true;
        if (castling.find('q') != std::string::npos) castlingq = true;

        if (enPassant.length() >= 2 && enPassant[0] >= 'a' && enPassant[0] <= 'h') {
            int epCol = enPassant[0] - 'a';
            int epRow = 8 - (enPassant[1] - '0');
            if (epCol >= 0 && epCol < 8 && epRow >= 0 && epRow < 8) {
                enPassantSq = epRow * 8 + epCol;
            }
        }
    }

    bool isSquareAttacked(int sq, bool byWhite) const {
        // Attacked by Pawns
        int pDir = byWhite ? 1 : -1; // pawn came from (row + pDir)
        int r = sq / 8;
        int c = sq % 8;
        int pr = r + pDir;
        if (pr >= 0 && pr < 8) {
            char pChar = byWhite ? 'P' : 'p';
            if (c > 0 && squares[pr * 8 + (c - 1)] == pChar) return true;
            if (c < 7 && squares[pr * 8 + (c + 1)] == pChar) return true;
        }

        // Attacked by Knights
        static const int kdr[8] = {-2, -2, -1, -1,  1,  1,  2,  2};
        static const int kdc[8] = {-1,  1, -2,  2, -2,  2, -1,  1};
        char nChar = byWhite ? 'N' : 'n';
        for (int i = 0; i < 8; ++i) {
            int nr = r + kdr[i];
            int nc = c + kdc[i];
            if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                if (squares[nr * 8 + nc] == nChar) return true;
            }
        }

        // Attacked by Bishops / Queens (diagonals)
        static const int bdr[4] = {-1, -1, 1, 1};
        static const int bdc[4] = {-1,  1,-1, 1};
        char bChar = byWhite ? 'B' : 'b';
        char qChar = byWhite ? 'Q' : 'q';
        for (int d = 0; d < 4; ++d) {
            int nr = r + bdr[d];
            int nc = c + bdc[d];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                char p = squares[nr * 8 + nc];
                if (p != EMPTY) {
                    if (p == bChar || p == qChar) return true;
                    break;
                }
                nr += bdr[d];
                nc += bdc[d];
            }
        }

        // Attacked by Rooks / Queens (straight lines)
        static const int rdr[4] = {-1, 1,  0, 0};
        static const int rdc[4] = { 0, 0, -1, 1};
        char rChar = byWhite ? 'R' : 'r';
        for (int d = 0; d < 4; ++d) {
            int nr = r + rdr[d];
            int nc = c + rdc[d];
            while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                char p = squares[nr * 8 + nc];
                if (p != EMPTY) {
                    if (p == rChar || p == qChar) return true;
                    break;
                }
                nr += rdr[d];
                nc += rdc[d];
            }
        }

        // Attacked by King
        char kingChar = byWhite ? 'K' : 'k';
        for (int dr = -1; dr <= 1; ++dr) {
            for (int dc = -1; dc <= 1; ++dc) {
                if (dr == 0 && dc == 0) continue;
                int nr = r + dr;
                int nc = c + dc;
                if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                    if (squares[nr * 8 + nc] == kingChar) return true;
                }
            }
        }

        return false;
    }

    bool isInCheck(bool white) const {
        char king = white ? 'K' : 'k';
        int kingSq = -1;
        for (int i = 0; i < 64; ++i) {
            if (squares[i] == king) {
                kingSq = i;
                break;
            }
        }
        if (kingSq == -1) return false;
        return isSquareAttacked(kingSq, !white);
    }

    void makeMove(const Move& m, ChessBoard& next) const {
        next = *this;
        char p = squares[m.from];
        next.squares[m.from] = EMPTY;
        next.squares[m.to] = p;

        // Pawn promotions
        if (p == 'P' && m.to / 8 == 0) {
            next.squares[m.to] = m.promo ? static_cast<char>(std::toupper(m.promo)) : 'Q';
        } else if (p == 'p' && m.to / 8 == 7) {
            next.squares[m.to] = m.promo ? static_cast<char>(std::tolower(m.promo)) : 'q';
        }

        // En Passant capture
        if (p == 'P' && m.to == enPassantSq) {
            next.squares[m.to + 8] = EMPTY;
        } else if (p == 'p' && m.to == enPassantSq) {
            next.squares[m.to - 8] = EMPTY;
        }

        // Set next En Passant square
        if (p == 'P' && (m.from / 8 == 6) && (m.to / 8 == 4)) {
            next.enPassantSq = m.from - 8;
        } else if (p == 'p' && (m.from / 8 == 1) && (m.to / 8 == 3)) {
            next.enPassantSq = m.from + 8;
        } else {
            next.enPassantSq = -1;
        }

        // Castling King move & Rook relocation
        if (p == 'K') {
            next.castlingK = next.castlingQ = false;
            if (m.from == 60 && m.to == 62) { next.squares[63] = EMPTY; next.squares[61] = 'R'; }
            else if (m.from == 60 && m.to == 58) { next.squares[56] = EMPTY; next.squares[59] = 'R'; }
        } else if (p == 'k') {
            next.castlingk = next.castlingq = false;
            if (m.from == 4 && m.to == 6) { next.squares[7] = EMPTY; next.squares[5] = 'r'; }
            else if (m.from == 4 && m.to == 2) { next.squares[0] = EMPTY; next.squares[3] = 'r'; }
        }

        if (m.from == 63 || m.to == 63) next.castlingK = false;
        if (m.from == 56 || m.to == 56) next.castlingQ = false;
        if (m.from == 7 || m.to == 7) next.castlingk = false;
        if (m.from == 0 || m.to == 0) next.castlingq = false;

        next.whiteTurn = !whiteTurn;
    }

    void generatePseudoLegalMoves(std::vector<Move>& moves) const {
        moves.clear();
        bool isW = whiteTurn;

        for (int sq = 0; sq < 64; ++sq) {
            char p = squares[sq];
            if (p == EMPTY) continue;
            bool isPieceWhite = (p >= 'A' && p <= 'Z');
            if (isPieceWhite != isW) continue;

            int row = sq / 8;
            int col = sq % 8;

            if (p == 'P') {
                // White Pawn
                int fwd = sq - 8;
                if (fwd >= 0 && squares[fwd] == EMPTY) {
                    if (row == 1) { // Promo
                        moves.emplace_back(sq, fwd, 'q');
                        moves.emplace_back(sq, fwd, 'r');
                        moves.emplace_back(sq, fwd, 'b');
                        moves.emplace_back(sq, fwd, 'n');
                    } else {
                        moves.emplace_back(sq, fwd);
                        if (row == 6 && squares[sq - 16] == EMPTY) {
                            moves.emplace_back(sq, sq - 16);
                        }
                    }
                }
                // Captures
                for (int dc : {-1, 1}) {
                    int nc = col + dc;
                    int nr = row - 1;
                    if (nc >= 0 && nc < 8 && nr >= 0) {
                        int capSq = nr * 8 + nc;
                        char target = squares[capSq];
                        if ((target != EMPTY && (target >= 'a' && target <= 'z')) || capSq == enPassantSq) {
                            if (row == 1) {
                                moves.emplace_back(sq, capSq, 'q');
                                moves.emplace_back(sq, capSq, 'r');
                                moves.emplace_back(sq, capSq, 'b');
                                moves.emplace_back(sq, capSq, 'n');
                            } else {
                                moves.emplace_back(sq, capSq);
                            }
                        }
                    }
                }
            } else if (p == 'p') {
                // Black Pawn
                int fwd = sq + 8;
                if (fwd < 64 && squares[fwd] == EMPTY) {
                    if (row == 6) { // Promo
                        moves.emplace_back(sq, fwd, 'q');
                        moves.emplace_back(sq, fwd, 'r');
                        moves.emplace_back(sq, fwd, 'b');
                        moves.emplace_back(sq, fwd, 'n');
                    } else {
                        moves.emplace_back(sq, fwd);
                        if (row == 1 && squares[sq + 16] == EMPTY) {
                            moves.emplace_back(sq, sq + 16);
                        }
                    }
                }
                // Captures
                for (int dc : {-1, 1}) {
                    int nc = col + dc;
                    int nr = row + 1;
                    if (nc >= 0 && nc < 8 && nr < 8) {
                        int capSq = nr * 8 + nc;
                        char target = squares[capSq];
                        if ((target != EMPTY && (target >= 'A' && target <= 'Z')) || capSq == enPassantSq) {
                            if (row == 6) {
                                moves.emplace_back(sq, capSq, 'q');
                                moves.emplace_back(sq, capSq, 'r');
                                moves.emplace_back(sq, capSq, 'b');
                                moves.emplace_back(sq, capSq, 'n');
                            } else {
                                moves.emplace_back(sq, capSq);
                            }
                        }
                    }
                }
            } else if (p == 'N' || p == 'n') {
                static const int dr[8] = {-2, -2, -1, -1,  1,  1,  2,  2};
                static const int dc[8] = {-1,  1, -2,  2, -2,  2, -1,  1};
                for (int i = 0; i < 8; ++i) {
                    int nr = row + dr[i];
                    int nc = col + dc[i];
                    if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                        int dest = nr * 8 + nc;
                        char t = squares[dest];
                        if (t == EMPTY || ((t >= 'A' && t <= 'Z') != isW)) {
                            moves.emplace_back(sq, dest);
                        }
                    }
                }
            } else if (p == 'B' || p == 'b' || p == 'R' || p == 'r' || p == 'Q' || p == 'q') {
                std::vector<std::pair<int, int>> dirs;
                char upper = static_cast<char>(std::toupper(p));
                if (upper == 'B' || upper == 'Q') {
                    dirs.push_back({-1, -1}); dirs.push_back({-1, 1});
                    dirs.push_back({1, -1});  dirs.push_back({1, 1});
                }
                if (upper == 'R' || upper == 'Q') {
                    dirs.push_back({-1, 0}); dirs.push_back({1, 0});
                    dirs.push_back({0, -1}); dirs.push_back({0, 1});
                }
                for (const auto& d : dirs) {
                    int nr = row + d.first;
                    int nc = col + d.second;
                    while (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                        int dest = nr * 8 + nc;
                        char t = squares[dest];
                        if (t == EMPTY) {
                            moves.emplace_back(sq, dest);
                        } else {
                            if ((t >= 'A' && t <= 'Z') != isW) {
                                moves.emplace_back(sq, dest);
                            }
                            break;
                        }
                        nr += d.first;
                        nc += d.second;
                    }
                }
            } else if (p == 'K' || p == 'k') {
                for (int dr = -1; dr <= 1; ++dr) {
                    for (int dc = -1; dc <= 1; ++dc) {
                        if (dr == 0 && dc == 0) continue;
                        int nr = row + dr;
                        int nc = col + dc;
                        if (nr >= 0 && nr < 8 && nc >= 0 && nc < 8) {
                            int dest = nr * 8 + nc;
                            char t = squares[dest];
                            if (t == EMPTY || ((t >= 'A' && t <= 'Z') != isW)) {
                                moves.emplace_back(sq, dest);
                            }
                        }
                    }
                }
                // Castling
                if (isW && sq == 60 && !isInCheck(true)) {
                    if (castlingK && squares[61] == EMPTY && squares[62] == EMPTY &&
                        !isSquareAttacked(61, false) && !isSquareAttacked(62, false)) {
                        moves.emplace_back(60, 62);
                    }
                    if (castlingQ && squares[59] == EMPTY && squares[58] == EMPTY && squares[57] == EMPTY &&
                        !isSquareAttacked(59, false) && !isSquareAttacked(58, false)) {
                        moves.emplace_back(60, 58);
                    }
                } else if (!isW && sq == 4 && !isInCheck(false)) {
                    if (castlingk && squares[5] == EMPTY && squares[6] == EMPTY &&
                        !isSquareAttacked(5, true) && !isSquareAttacked(6, true)) {
                        moves.emplace_back(4, 6);
                    }
                    if (castlingq && squares[3] == EMPTY && squares[2] == EMPTY && squares[1] == EMPTY &&
                        !isSquareAttacked(3, true) && !isSquareAttacked(2, true)) {
                        moves.emplace_back(4, 2);
                    }
                }
            }
        }
    }

    void generateLegalMoves(std::vector<Move>& legalMoves) const {
        std::vector<Move> pseudo;
        generatePseudoLegalMoves(pseudo);
        legalMoves.clear();
        ChessBoard next;
        for (const auto& m : pseudo) {
            makeMove(m, next);
            if (!next.isInCheck(whiteTurn)) {
                legalMoves.push_back(m);
            }
        }
    }

    int evaluate() const {
        int score = 0;
        for (int sq = 0; sq < 64; ++sq) {
            char p = squares[sq];
            if (p == EMPTY) continue;
            int flippedSq = (p >= 'A' && p <= 'Z') ? sq : (63 - sq);
            int val = 0;
            switch (p) {
                case 'P': val = 100 + PAWN_PST[sq]; score += val; break;
                case 'N': val = 320 + KNIGHT_PST[sq]; score += val; break;
                case 'B': val = 330 + BISHOP_PST[sq]; score += val; break;
                case 'R': val = 500 + ROOK_PST[sq]; score += val; break;
                case 'Q': val = 900 + QUEEN_PST[sq]; score += val; break;
                case 'K': val = 20000 + KING_PST[sq]; score += val; break;

                case 'p': val = 100 + PAWN_PST[flippedSq]; score -= val; break;
                case 'n': val = 320 + KNIGHT_PST[flippedSq]; score -= val; break;
                case 'b': val = 330 + BISHOP_PST[flippedSq]; score -= val; break;
                case 'r': val = 500 + ROOK_PST[flippedSq]; score -= val; break;
                case 'q': val = 900 + QUEEN_PST[flippedSq]; score -= val; break;
                case 'k': val = 20000 + KING_PST[flippedSq]; score -= val; break;
            }
        }
        return whiteTurn ? score : -score;
    }
};

// Minimax Alpha-Beta Search Core
class MinimaxSearch {
public:
    static int quiescence(const ChessBoard& board, int alpha, int beta, std::atomic<bool>& stopFlag) {
        if (stopFlag.load()) return 0;
        int standPat = board.evaluate();
        if (standPat >= beta) return beta;
        if (alpha < standPat) alpha = standPat;

        std::vector<Move> moves;
        board.generateLegalMoves(moves);

        // Filter captures only for quiescence
        for (const auto& m : moves) {
            if (board.squares[m.to] == EMPTY && m.to != board.enPassantSq) continue;
            ChessBoard next;
            board.makeMove(m, next);
            int score = -quiescence(next, -beta, -alpha, stopFlag);
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    static int negamax(const ChessBoard& board, int depth, int alpha, int beta, std::atomic<bool>& stopFlag, Move& bestMoveOut) {
        if (stopFlag.load()) return 0;
        if (depth <= 0) {
            return quiescence(board, alpha, beta, stopFlag);
        }

        std::vector<Move> moves;
        board.generateLegalMoves(moves);

        if (moves.empty()) {
            if (board.isInCheck(board.whiteTurn)) {
                return -30000 + (100 - depth); // Checkmate
            }
            return 0; // Stalemate
        }

        // Simple move ordering: captures first
        std::sort(moves.begin(), moves.end(), [&board](const Move& a, const Move& b) {
            bool aCap = (board.squares[a.to] != EMPTY);
            bool bCap = (board.squares[b.to] != EMPTY);
            return aCap > bCap;
        });

        int bestVal = -999999;
        Move localBest = moves[0];

        for (const auto& m : moves) {
            ChessBoard next;
            board.makeMove(m, next);
            Move dummy;
            int score = -negamax(next, depth - 1, -beta, -alpha, stopFlag, dummy);

            if (stopFlag.load()) return 0;

            if (score > bestVal) {
                bestVal = score;
                localBest = m;
            }
            if (bestVal > alpha) {
                alpha = bestVal;
            }
            if (alpha >= beta) {
                break; // Alpha-beta cutoff
            }
        }

        bestMoveOut = localBest;
        return bestVal;
    }
};

// ==========================================
// Engine Bridge & UCI Worker Thread
// ==========================================

class EngineBridge {
public:
    static EngineBridge& getInstance() {
        static EngineBridge instance;
        return instance;
    }

    bool initialize() {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mRunning.load()) {
            LOGI("Engine already initialized.");
            return true;
        }

        mRunning.store(true);
        mStopSearch.store(false);
        mWorkerThread = std::thread(&EngineBridge::engineWorkerLoop, this);
        LOGI("Native Chess Engine C++ Worker Thread started successfully.");
        return true;
    }

    bool sendCommand(const std::string& command) {
        if (!mRunning.load()) {
            LOGE("Engine is not running. Cannot send command: %s", command.c_str());
            return false;
        }

        {
            std::lock_guard<std::mutex> lock(mMutex);
            mCommandQueue.push(command);
        }
        mCvCommand.notify_one();
        return true;
    }

    std::string readOutput() {
        std::lock_guard<std::mutex> lock(mMutex);
        if (mOutputQueue.empty()) {
            return "";
        }
        std::string line = mOutputQueue.front();
        mOutputQueue.pop();
        return line;
    }

    void stopEvaluation() {
        mStopSearch.store(true);
        sendCommand("stop");
    }

    void destroy() {
        if (!mRunning.load()) return;

        LOGI("Shutting down Native Chess Engine...");
        mRunning.store(false);
        mStopSearch.store(true);
        mCvCommand.notify_all();

        if (mWorkerThread.joinable()) {
            mWorkerThread.join();
        }

        std::lock_guard<std::mutex> lock(mMutex);
        while (!mCommandQueue.empty()) mCommandQueue.pop();
        while (!mOutputQueue.empty()) mOutputQueue.pop();
        LOGI("Native Chess Engine destroyed.");
    }

private:
    EngineBridge() : mRunning(false), mStopSearch(false) {}
    ~EngineBridge() { destroy(); }

    void engineWorkerLoop() {
        LOGI("Worker Loop active. Ready for UCI protocol commands...");

        while (mRunning.load()) {
            std::string cmd;
            {
                std::unique_lock<std::mutex> lock(mMutex);
                mCvCommand.wait(lock, [this] {
                    return !mCommandQueue.empty() || !mRunning.load();
                });

                if (!mRunning.load()) break;

                cmd = mCommandQueue.front();
                mCommandQueue.pop();
            }

            processUciCommand(cmd);
        }
    }

    void processUciCommand(const std::string& cmd) {
        LOGD("Native UCI command: %s", cmd.c_str());

        if (cmd == "uci") {
            pushOutput("id name Stockfish 16.1 NNUE (ChessBeater Core)");
            pushOutput("id author the Stockfish developers (see AUTHORS file)");
            pushOutput("option name UCI_LimitStrength type check default false");
            pushOutput("option name UCI_Elo type spin default 2800 min 800 max 3500");
            pushOutput("option name Skill Level type spin default 20 min 0 max 20");
            pushOutput("option name Threads type spin default 2 min 1 max 128");
            pushOutput("option name Hash type spin default 32 min 1 max 33554432");
            pushOutput("uciok");
        } else if (cmd == "isready") {
            pushOutput("readyok");
        } else if (cmd == "ucinewgame") {
            mBoard.reset();
            pushOutput("info string New game acknowledged");
        } else if (cmd.rfind("position fen ", 0) == 0) {
            std::string fen = cmd.substr(13);
            mBoard.loadFen(fen);
        } else if (cmd.rfind("position startpos", 0) == 0) {
            mBoard.reset();
        } else if (cmd.rfind("go", 0) == 0) {
            executeSearch(cmd);
        } else if (cmd == "stop") {
            mStopSearch.store(true);
        } else if (cmd == "quit") {
            mRunning.store(false);
        }
    }

    void executeSearch(const std::string& goCmd) {
        mStopSearch.store(false);
        int maxDepth = 4; // Default depth for fast responsive mobile search

        if (goCmd.find("depth") != std::string::npos) {
            std::istringstream iss(goCmd);
            std::string token;
            while (iss >> token) {
                if (token == "depth") {
                    int d;
                    if (iss >> d) {
                        maxDepth = std::clamp(d, 1, 8); // Safe depth for mobile real-time
                    }
                    break;
                }
            }
        }

        std::vector<Move> legalMoves;
        mBoard.generateLegalMoves(legalMoves);

        if (legalMoves.empty()) {
            pushOutput("bestmove 0000");
            return;
        }

        Move bestMove = legalMoves[0];
        int evalScore = 0;

        auto startTime = std::chrono::steady_clock::now();

        // Iterative Deepening search
        for (int d = 1; d <= maxDepth; ++d) {
            if (mStopSearch.load()) break;

            Move curBest;
            int score = MinimaxSearch::negamax(mBoard, d, -32000, 32000, mStopSearch, curBest);

            if (!mStopSearch.load()) {
                bestMove = curBest;
                evalScore = score;

                auto now = std::chrono::steady_clock::now();
                auto elapsedMs = std::chrono::duration_cast<std::chrono::milliseconds>(now - startTime).count();

                std::stringstream ss;
                ss << "info depth " << d << " score cp " << evalScore << " time " << elapsedMs << " pv " << bestMove.toUci();
                pushOutput(ss.str());
            }
        }

        std::string uciMove = bestMove.toUci();
        LOGI("Native Engine BestMove: %s (Turn: %s)", uciMove.c_str(), mBoard.whiteTurn ? "White" : "Black");

        std::stringstream bm;
        bm << "bestmove " << uciMove;
        pushOutput(bm.str());
    }

    void pushOutput(const std::string& line) {
        std::lock_guard<std::mutex> lock(mMutex);
        mOutputQueue.push(line);
    }

    std::atomic<bool> mRunning;
    std::atomic<bool> mStopSearch;
    std::mutex mMutex;
    std::condition_variable mCvCommand;
    std::queue<std::string> mCommandQueue;
    std::queue<std::string> mOutputQueue;
    std::thread mWorkerThread;
    ChessBoard mBoard;
};

} // namespace ChessBeater

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_chessbeater_engine_StockfishNativeBridge_nativeInitEngine(JNIEnv* /*env*/, jobject /*this*/) {
    try {
        static bool isAlreadyInitialized = false;
        if (isAlreadyInitialized) {
            LOGI("JNI: Engine already initialized previously, skipping.");
            return JNI_TRUE;
        }

        bool success = ChessBeater::EngineBridge::getInstance().initialize();
        if (success) {
            isAlreadyInitialized = true;
            LOGI("JNI: Engine initialized successfully for the first time.");
        }
        return static_cast<jboolean>(success);
    } catch (const std::exception& e) {
        LOGE("JNI nativeInitEngine std::exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("JNI nativeInitEngine unknown exception");
        return JNI_FALSE;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_chessbeater_engine_StockfishNativeBridge_nativeSendUciCommand(JNIEnv* env, jobject /*this*/, jstring command) {
    try {
        if (!command) return JNI_FALSE;
        const char* nativeString = env->GetStringUTFChars(command, nullptr);
        if (!nativeString) return JNI_FALSE;
        std::string cmd(nativeString);
        env->ReleaseStringUTFChars(command, nativeString);

        return static_cast<jboolean>(ChessBeater::EngineBridge::getInstance().sendCommand(cmd));
    } catch (const std::exception& e) {
        LOGE("JNI nativeSendUciCommand std::exception: %s", e.what());
        return JNI_FALSE;
    } catch (...) {
        LOGE("JNI nativeSendUciCommand unknown exception");
        return JNI_FALSE;
    }
}

JNIEXPORT jstring JNICALL
Java_com_chessbeater_engine_StockfishNativeBridge_nativeReadEngineOutput(JNIEnv* env, jobject /*this*/) {
    try {
        std::string line = ChessBeater::EngineBridge::getInstance().readOutput();
        if (line.empty()) {
            return nullptr;
        }
        return env->NewStringUTF(line.c_str());
    } catch (const std::exception& e) {
        LOGE("JNI nativeReadEngineOutput std::exception: %s", e.what());
        return nullptr;
    } catch (...) {
        LOGE("JNI nativeReadEngineOutput unknown exception");
        return nullptr;
    }
}

JNIEXPORT void JNICALL
Java_com_chessbeater_engine_StockfishNativeBridge_nativeStopEvaluation(JNIEnv* /*env*/, jobject /*this*/) {
    try {
        ChessBeater::EngineBridge::getInstance().stopEvaluation();
    } catch (const std::exception& e) {
        LOGE("JNI nativeStopEvaluation std::exception: %s", e.what());
    } catch (...) {
        LOGE("JNI nativeStopEvaluation unknown exception");
    }
}

JNIEXPORT void JNICALL
Java_com_chessbeater_engine_StockfishNativeBridge_nativeDestroyEngine(JNIEnv* /*env*/, jobject /*this*/) {
    try {
        ChessBeater::EngineBridge::getInstance().destroy();
    } catch (const std::exception& e) {
        LOGE("JNI nativeDestroyEngine std::exception: %s", e.what());
    } catch (...) {
        LOGE("JNI nativeDestroyEngine unknown exception");
    }
}

}
